// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.24;

interface IERC20 {
    function transfer(address to, uint256 amount) external returns (bool);
    function transferFrom(address from, address to, uint256 amount) external returns (bool);
    function balanceOf(address who) external view returns (uint256);
}

/// @title TorihikiBridge
/// @notice Escrow for torihiki collateral, controlled by torihiki's own validator set.
///
/// Roadmap D3 (kotoba-lang/torihiki docs/decentralization-roadmap.md). The shape is
/// Hyperliquid's Bridge2, for the same reasons:
///
/// - Deposits are plain token transfers that emit `Deposit`. torihiki validators
///   observe the event and credit the account only when a quorum of them attests
///   the same deposit (`torihiki.state :deposit-attest`). Nothing here trusts a
///   relayer: the event is the evidence.
/// - A withdrawal pays out only with signatures from validators holding MORE THAN
///   2/3 of the current set's power, and only after `disputePeriod`. During that
///   window any single validator can pause the bridge ("locker"), and a 2/3 quorum
///   can invalidate the request. One compromised key can stop the bridge; it cannot
///   empty it.
/// - The validator set is updated by the current set itself (2/3), after the same
///   dispute period. There is no owner, no admin key and no upgrade proxy.
///
/// Validator keys here are secp256k1 BRIDGE SIGNERS, registered on torihiki beside
/// each validator's Ed25519 consensus key, because the EVM verifies one cheaply and
/// not the other.
///
/// Every signed message is EIP-712 typed data bound to this contract, this EVM chain
/// and a torihiki chain id, so a signature for one deployment or one torihiki chain
/// cannot be replayed on another.
contract TorihikiBridge {
    // ── configuration ───────────────────────────────────────────────────────────

    IERC20 public immutable token;
    /// keccak256 of the torihiki chain id string (e.g. "torihiki-1").
    bytes32 public immutable torihikiChain;
    /// Seconds between a quorum-signed request and its execution.
    uint64 public immutable disputePeriod;

    /// torihiki's engine is exact over i53. A deposit it cannot represent is refused
    /// here rather than rounded there.
    uint256 public constant MAX_AMOUNT = 2 ** 53 - 1;

    // ── validator set ──────────────────────────────────────────────────────────

    uint64 public epoch;
    address[] internal signers;
    mapping(address => uint64) public powerOf;
    uint256 public totalPower;

    struct PendingSet {
        uint64 epoch;
        uint64 requestedAt;
        address[] signers;
        uint64[] powers;
    }

    PendingSet internal pendingSet;

    // ── state ──────────────────────────────────────────────────────────────────

    bool public paused;
    /// Total the escrow may hold. Raised only by quorum; a cap is how an unaudited
    /// bridge earns trust with bounded loss.
    uint256 public depositCap;
    uint64 public depositSeq;
    /// Replay protection for quorum actions, one counter per action kind, so
    /// two actions signed at the same moment cannot cancel each other.
    mapping(bytes32 => uint64) public actionNonce;
    /// After a quorum unpause, a single locker cannot pause again until this
    /// time. Otherwise one faulty validator re-pauses after every unpause and
    /// the quorum can never finalize the set change that would remove it.
    uint256 public pauseDisabledUntil;
    uint256 private locked = 1;

    struct Withdrawal {
        address dest;
        uint256 amount;
        uint64 requestedAt;
        bool finalized;
        bool invalidated;
    }

    /// torihiki withdrawal claim id => request. A claim can be requested once.
    mapping(uint64 => Withdrawal) public withdrawals;

    // ── events ─────────────────────────────────────────────────────────────────

    event Deposit(uint64 indexed account, address indexed from, uint256 amount, uint64 seq);
    event WithdrawalRequested(uint64 indexed claim, address indexed dest, uint256 amount, uint64 epoch);
    event Withdrawn(uint64 indexed claim, address indexed dest, uint256 amount);
    event WithdrawalInvalidated(uint64 indexed claim);
    event ValidatorSetRequested(uint64 indexed epoch, address[] signers, uint64[] powers);
    event ValidatorSetUpdated(uint64 indexed epoch, address[] signers, uint64[] powers);
    event Paused(address indexed by);
    event Unpaused();
    event DepositCapChanged(uint256 cap);
    event ValidatorSetCancelled(uint64 indexed epoch);

    // ── errors ─────────────────────────────────────────────────────────────────

    error IsPaused();
    error BadAmount();
    error BadAccount();
    error CapExceeded();
    error TransferFailed();
    error AlreadyRequested();
    error NotRequested();
    error AlreadyDone();
    error DisputeWindow();
    error NoQuorum();
    error BadSignature();
    error SignersNotSorted();
    error StaleEpoch();
    error BadSet();
    error NotASigner();
    error PauseCooldown();
    error Reentrant();

    // ── EIP-712 ────────────────────────────────────────────────────────────────

    bytes32 internal constant DOMAIN_TYPEHASH =
        keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)");
    bytes32 public constant WITHDRAWAL_TYPEHASH =
        keccak256("Withdrawal(bytes32 chain,uint64 claim,address dest,uint256 amount,uint64 epoch)");
    /// `fromEpoch` is the set doing the signing: a proposal signed by an earlier
    /// set, held back and submitted later, does not verify against this one.
    bytes32 public constant VALIDATOR_SET_TYPEHASH =
        keccak256("ValidatorSet(bytes32 chain,uint64 fromEpoch,uint64 epoch,address[] signers,uint64[] powers)");
    bytes32 public constant ACTION_TYPEHASH =
        keccak256("Action(bytes32 chain,uint64 epoch,string kind,uint64 claim,uint256 value,uint64 nonce)");

    constructor(
        IERC20 token_,
        string memory torihikiChainId,
        uint64 disputePeriod_,
        uint256 depositCap_,
        address[] memory signers_,
        uint64[] memory powers_
    ) {
        token = token_;
        torihikiChain = keccak256(bytes(torihikiChainId));
        disputePeriod = disputePeriod_;
        depositCap = depositCap_;
        _install(0, signers_, powers_);
    }

    function domainSeparator() public view returns (bytes32) {
        return keccak256(
            abi.encode(DOMAIN_TYPEHASH, keccak256("torihiki-bridge"), keccak256("1"), block.chainid, address(this))
        );
    }

    function _digest(bytes32 structHash) internal view returns (bytes32) {
        return keccak256(abi.encodePacked("\x19\x01", domainSeparator(), structHash));
    }

    function withdrawalDigest(uint64 claim, address dest, uint256 amount, uint64 epoch_)
        public
        view
        returns (bytes32)
    {
        return _digest(keccak256(abi.encode(WITHDRAWAL_TYPEHASH, torihikiChain, claim, dest, amount, epoch_)));
    }

    function validatorSetDigest(uint64 epoch_, address[] memory signers_, uint64[] memory powers_)
        public
        view
        returns (bytes32)
    {
        return _digest(
            keccak256(
                abi.encode(
                    VALIDATOR_SET_TYPEHASH,
                    torihikiChain,
                    epoch,
                    epoch_,
                    keccak256(abi.encodePacked(signers_)),
                    keccak256(abi.encodePacked(powers_))
                )
            )
        );
    }

    function actionDigest(string memory kind, uint64 claim, uint256 value, uint64 nonce)
        public
        view
        returns (bytes32)
    {
        return _digest(
            keccak256(abi.encode(ACTION_TYPEHASH, torihikiChain, epoch, keccak256(bytes(kind)), claim, value, nonce))
        );
    }

    modifier nonReentrant() {
        if (locked != 1) revert Reentrant();
        locked = 2;
        _;
        locked = 1;
    }

    /// Check a quorum action and consume its kind's nonce.
    function _action(string memory kind, uint64 claim, uint256 value, bytes[] calldata sigs) internal {
        bytes32 k = keccak256(bytes(kind));
        _requireQuorum(actionDigest(kind, claim, value, actionNonce[k]), sigs);
        actionNonce[k]++;
    }

    function nonceOf(string calldata kind) external view returns (uint64) {
        return actionNonce[keccak256(bytes(kind))];
    }

    // ── reading ────────────────────────────────────────────────────────────────

    function currentSigners() external view returns (address[] memory) {
        return signers;
    }

    function pendingValidatorSet()
        external
        view
        returns (uint64 epoch_, uint64 requestedAt, address[] memory signers_, uint64[] memory powers_)
    {
        return (pendingSet.epoch, pendingSet.requestedAt, pendingSet.signers, pendingSet.powers);
    }

    // ── deposits ───────────────────────────────────────────────────────────────

    /// @notice Escrow `amount` for torihiki account `account`.
    /// @dev Credited by the balance delta, not by `amount`, so a token that takes a
    ///      fee in transfer cannot make the escrow promise more than it holds.
    function deposit(uint64 account, uint256 amount) external nonReentrant {
        if (paused) revert IsPaused();
        // torihiki account ids are i53, like every other number in its engine; a
        // deposit to an id it cannot represent would be skipped by every
        // validator and never credited.
        if (account == 0 || account > MAX_AMOUNT) revert BadAccount();
        if (amount == 0 || amount > MAX_AMOUNT) revert BadAmount();
        uint256 before = token.balanceOf(address(this));
        if (before + amount > depositCap) revert CapExceeded();
        if (!token.transferFrom(msg.sender, address(this), amount)) revert TransferFailed();
        uint256 received = token.balanceOf(address(this)) - before;
        if (received == 0 || received > MAX_AMOUNT) revert BadAmount();
        uint64 seq = ++depositSeq;
        emit Deposit(account, msg.sender, received, seq);
    }

    // ── withdrawals ────────────────────────────────────────────────────────────

    /// @notice Record a withdrawal the current validator set signed. Pays nothing yet.
    /// @param sigs 65-byte signatures, ordered by strictly ascending signer address.
    function requestWithdrawal(uint64 claim, address dest, uint256 amount, bytes[] calldata sigs) external {
        if (paused) revert IsPaused();
        if (amount == 0 || amount > MAX_AMOUNT || dest == address(0)) revert BadAmount();
        Withdrawal storage w = withdrawals[claim];
        if (w.requestedAt != 0) revert AlreadyRequested();
        _requireQuorum(withdrawalDigest(claim, dest, amount, epoch), sigs);
        withdrawals[claim] = Withdrawal(dest, amount, uint64(block.timestamp), false, false);
        emit WithdrawalRequested(claim, dest, amount, epoch);
    }

    /// @notice Pay a requested withdrawal once its dispute period has passed.
    ///         Anybody may call it; the destination was fixed by the signatures.
    function finalizeWithdrawal(uint64 claim) external nonReentrant {
        if (paused) revert IsPaused();
        Withdrawal storage w = withdrawals[claim];
        if (w.requestedAt == 0) revert NotRequested();
        if (w.finalized || w.invalidated) revert AlreadyDone();
        if (block.timestamp < uint256(w.requestedAt) + disputePeriod) revert DisputeWindow();
        w.finalized = true;
        if (!token.transfer(w.dest, w.amount)) revert TransferFailed();
        emit Withdrawn(claim, w.dest, w.amount);
    }

    /// @notice Cancel a claim (2/3): a pending request the validators did not mean
    ///         to sign, or a claim that was never requested and never will be —
    ///         whose id is then burned so it cannot be requested later. Either way
    ///         `WithdrawalInvalidated` is what lets torihiki refund the owner.
    function invalidateWithdrawal(uint64 claim, bytes[] calldata sigs) external {
        Withdrawal storage w = withdrawals[claim];
        if (w.finalized || w.invalidated) revert AlreadyDone();
        _action("invalidate", claim, 0, sigs);
        if (w.requestedAt == 0) {
            w.requestedAt = uint64(block.timestamp);
        }
        w.invalidated = true;
        emit WithdrawalInvalidated(claim);
    }

    // ── the locker and its release ─────────────────────────────────────────────

    /// @notice Any single current validator can stop every outflow.
    function pause() external {
        if (powerOf[msg.sender] == 0) revert NotASigner();
        if (block.timestamp < pauseDisabledUntil) revert PauseCooldown();
        paused = true;
        emit Paused(msg.sender);
    }

    /// @notice Only a 2/3 quorum can start it again.
    ///         It also restarts the dispute window of a pending set change (time
    ///         spent paused is not time anybody could object in), and stops single
    ///         lockers for two dispute periods, so the quorum can finalize the set
    ///         change that removes a locker who keeps pausing.
    function unpause(bytes[] calldata sigs) external {
        _action("unpause", 0, 0, sigs);
        paused = false;
        if (pendingSet.requestedAt != 0) pendingSet.requestedAt = uint64(block.timestamp);
        pauseDisabledUntil = block.timestamp + 2 * uint256(disputePeriod);
        emit Unpaused();
    }

    function setDepositCap(uint256 cap, bytes[] calldata sigs) external {
        _action("deposit-cap", 0, cap, sigs);
        depositCap = cap;
        emit DepositCapChanged(cap);
    }

    // ── the validator set ──────────────────────────────────────────────────────

    /// @notice The current set (2/3) names its successor for torihiki epoch `newEpoch`.
    ///         Takes effect after the dispute period, like a withdrawal, and for the
    ///         same reason: a set is the authority to sign every future withdrawal.
    function requestValidatorSet(
        uint64 newEpoch,
        address[] calldata newSigners,
        uint64[] calldata newPowers,
        bytes[] calldata sigs
    ) external {
        if (paused) revert IsPaused();
        if (newEpoch <= epoch || newEpoch <= pendingSet.epoch) revert StaleEpoch();
        _checkSet(newSigners, newPowers);
        _requireQuorum(validatorSetDigest(newEpoch, newSigners, newPowers), sigs);
        pendingSet.epoch = newEpoch;
        pendingSet.requestedAt = uint64(block.timestamp);
        pendingSet.signers = newSigners;
        pendingSet.powers = newPowers;
        emit ValidatorSetRequested(newEpoch, newSigners, newPowers);
    }

    /// @notice Drop a pending set change (2/3), e.g. one a locker paused on.
    function cancelValidatorSet(bytes[] calldata sigs) external {
        if (pendingSet.requestedAt == 0) revert NotRequested();
        _action("cancel-set", pendingSet.epoch, 0, sigs);
        uint64 e = pendingSet.epoch;
        delete pendingSet;
        emit ValidatorSetCancelled(e);
    }

    function finalizeValidatorSet() external {
        if (paused) revert IsPaused();
        if (pendingSet.requestedAt == 0) revert NotRequested();
        if (block.timestamp < uint256(pendingSet.requestedAt) + disputePeriod) revert DisputeWindow();
        uint64 e = pendingSet.epoch;
        address[] memory s = pendingSet.signers;
        uint64[] memory p = pendingSet.powers;
        delete pendingSet;
        _install(e, s, p);
    }

    // ── internals ──────────────────────────────────────────────────────────────

    function _checkSet(address[] memory s, uint64[] memory p) internal pure {
        // Four or more: the smallest set that tolerates one fault, the same floor
        // `torihiki.validators/min-set-size` holds the chain to.
        if (s.length < 4 || s.length != p.length) revert BadSet();
        for (uint256 i = 0; i < s.length; i++) {
            if (p[i] == 0) revert BadSet();
            if (i > 0 && s[i] <= s[i - 1]) revert SignersNotSorted();
        }
        if (s[0] == address(0)) revert BadSet();
    }

    function _install(uint64 e, address[] memory s, uint64[] memory p) internal {
        _checkSet(s, p);
        for (uint256 i = 0; i < signers.length; i++) {
            powerOf[signers[i]] = 0;
        }
        delete signers;
        uint256 total;
        for (uint256 i = 0; i < s.length; i++) {
            signers.push(s[i]);
            powerOf[s[i]] = p[i];
            total += p[i];
        }
        totalPower = total;
        epoch = e;
        emit ValidatorSetUpdated(e, s, p);
    }

    /// Signatures from the CURRENT set worth more than 2/3 of its power, each signer
    /// counted once (enforced by strictly ascending recovered addresses).
    function _requireQuorum(bytes32 digest, bytes[] calldata sigs) internal view {
        uint256 power;
        address last;
        for (uint256 i = 0; i < sigs.length; i++) {
            address who = _recover(digest, sigs[i]);
            if (who <= last) revert SignersNotSorted();
            last = who;
            power += powerOf[who];
        }
        if (power * 3 <= totalPower * 2) revert NoQuorum();
    }

    function _recover(bytes32 digest, bytes calldata sig) internal pure returns (address) {
        if (sig.length != 65) revert BadSignature();
        bytes32 r = bytes32(sig[0:32]);
        bytes32 s = bytes32(sig[32:64]);
        uint8 v = uint8(sig[64]);
        // Reject the high-s twin of every signature (EIP-2), so a signature cannot be
        // mutated into a second valid one.
        if (uint256(s) > 0x7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0) revert BadSignature();
        if (v != 27 && v != 28) revert BadSignature();
        address who = ecrecover(digest, v, r, s);
        if (who == address(0)) revert BadSignature();
        return who;
    }
}
