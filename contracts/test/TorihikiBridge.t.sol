// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.24;

import {TorihikiBridge, IERC20} from "../src/TorihikiBridge.sol";

interface Vm {
    function sign(uint256 pk, bytes32 digest) external returns (uint8 v, bytes32 r, bytes32 s);
    function addr(uint256 pk) external returns (address);
    function expectRevert(bytes4) external;
    function expectRevert() external;
    function prank(address) external;
    function warp(uint256) external;
    function chainId(uint256) external;
    function etch(address, bytes calldata) external;
}

contract MockUSDC is IERC20 {
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;

    function mint(address to, uint256 amount) external {
        balanceOf[to] += amount;
    }

    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        allowance[from][msg.sender] -= amount;
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

contract TorihikiBridgeTest {
    Vm constant vm = Vm(address(uint160(uint256(keccak256("hevm cheat code")))));

    MockUSDC usdc;
    TorihikiBridge bridge;
    uint256[] pks;
    address user = address(0xBEEF);
    uint64 constant DISPUTE = 200;

    // ── fixtures ───────────────────────────────────────────────────────────────

    /// Four signers, private keys chosen so the addresses sort in pk order.
    function _sortedKeys(uint256 n, uint256 seed) internal returns (uint256[] memory out) {
        out = new uint256[](n);
        for (uint256 i = 0; i < n; i++) {
            out[i] = seed + i;
        }
        // insertion sort by address
        for (uint256 i = 1; i < n; i++) {
            uint256 k = out[i];
            uint256 j = i;
            while (j > 0 && vm.addr(out[j - 1]) > vm.addr(k)) {
                out[j] = out[j - 1];
                j--;
            }
            out[j] = k;
        }
    }

    function _addrs(uint256[] memory ks) internal returns (address[] memory a) {
        a = new address[](ks.length);
        for (uint256 i = 0; i < ks.length; i++) {
            a[i] = vm.addr(ks[i]);
        }
    }

    function _powers(uint256 n) internal pure returns (uint64[] memory p) {
        p = new uint64[](n);
        for (uint256 i = 0; i < n; i++) {
            p[i] = 100;
        }
    }

    function setUp() public {
        usdc = new MockUSDC();
        uint256[] memory ks = _sortedKeys(4, 0xA11CE);
        for (uint256 i = 0; i < ks.length; i++) {
            pks.push(ks[i]);
        }
        bridge = new TorihikiBridge(IERC20(address(usdc)), "torihiki-1", DISPUTE, 1_000_000e6, _addrs(ks), _powers(4));
        usdc.mint(user, 10_000e6);
        vm.prank(user);
        usdc.approve(address(bridge), type(uint256).max);
    }

    function _sigs(bytes32 digest, uint256[] memory which) internal returns (bytes[] memory out) {
        out = new bytes[](which.length);
        for (uint256 i = 0; i < which.length; i++) {
            (uint8 v, bytes32 r, bytes32 s) = vm.sign(which[i], digest);
            out[i] = abi.encodePacked(r, s, v);
        }
    }

    function _first(uint256 n) internal view returns (uint256[] memory out) {
        out = new uint256[](n);
        for (uint256 i = 0; i < n; i++) {
            out[i] = pks[i];
        }
    }

    function _deposited(uint256 amount) internal {
        vm.prank(user);
        bridge.deposit(7, amount);
    }

    function _eq(uint256 a, uint256 b, string memory why) internal pure {
        require(a == b, why);
    }

    // ── deposits ───────────────────────────────────────────────────────────────

    function test_deposit_escrows_and_numbers() public {
        _deposited(500e6);
        _eq(usdc.balanceOf(address(bridge)), 500e6, "escrowed");
        _eq(bridge.depositSeq(), 1, "seq");
        _deposited(1e6);
        _eq(bridge.depositSeq(), 2, "seq increments");
    }

    function test_deposit_refuses_account_zero_and_huge() public {
        vm.prank(user);
        vm.expectRevert(TorihikiBridge.BadAccount.selector);
        bridge.deposit(0, 1);
        vm.prank(user);
        vm.expectRevert(TorihikiBridge.BadAmount.selector);
        bridge.deposit(7, 2 ** 53);
    }

    function test_deposit_cap() public {
        usdc.mint(user, 2_000_000e6);
        vm.prank(user);
        vm.expectRevert(TorihikiBridge.CapExceeded.selector);
        bridge.deposit(7, 1_000_001e6);
    }

    // ── withdrawals ────────────────────────────────────────────────────────────

    function test_withdrawal_needs_more_than_two_thirds() public {
        _deposited(500e6);
        bytes32 d = bridge.withdrawalDigest(1, user, 100e6, 0);
        // 2 of 4 = 1/2: refused. (3 of 4 is the quorum with equal power.)
        vm.expectRevert(TorihikiBridge.NoQuorum.selector);
        bridge.requestWithdrawal(1, user, 100e6, _sigs(d, _first(2)));
        bridge.requestWithdrawal(1, user, 100e6, _sigs(d, _first(3)));
    }

    function test_withdrawal_waits_out_the_dispute_period_then_pays() public {
        _deposited(500e6);
        bridge.requestWithdrawal(1, user, 100e6, _sigs(bridge.withdrawalDigest(1, user, 100e6, 0), _first(3)));
        vm.expectRevert(TorihikiBridge.DisputeWindow.selector);
        bridge.finalizeWithdrawal(1);
        vm.warp(block.timestamp + DISPUTE);
        uint256 before = usdc.balanceOf(user);
        bridge.finalizeWithdrawal(1);
        _eq(usdc.balanceOf(user) - before, 100e6, "paid");
        vm.expectRevert(TorihikiBridge.AlreadyDone.selector);
        bridge.finalizeWithdrawal(1);
    }

    function test_a_claim_is_requested_once() public {
        _deposited(500e6);
        bytes[] memory s = _sigs(bridge.withdrawalDigest(1, user, 100e6, 0), _first(3));
        bridge.requestWithdrawal(1, user, 100e6, s);
        vm.expectRevert(TorihikiBridge.AlreadyRequested.selector);
        bridge.requestWithdrawal(1, user, 100e6, s);
    }

    function test_signatures_bind_destination_and_amount() public {
        _deposited(500e6);
        bytes[] memory s = _sigs(bridge.withdrawalDigest(1, user, 100e6, 0), _first(3));
        // Re-aimed at another address: the recovered signers are strangers.
        vm.expectRevert();
        bridge.requestWithdrawal(1, address(0xBAD), 100e6, s);
        vm.expectRevert();
        bridge.requestWithdrawal(1, user, 400e6, s);
    }

    function test_a_signer_counts_once() public {
        _deposited(500e6);
        bytes32 d = bridge.withdrawalDigest(1, user, 100e6, 0);
        uint256[] memory dup = new uint256[](3);
        dup[0] = pks[0];
        dup[1] = pks[0];
        dup[2] = pks[1];
        vm.expectRevert(TorihikiBridge.SignersNotSorted.selector);
        bridge.requestWithdrawal(1, user, 100e6, _sigs(d, dup));
    }

    function test_high_s_twin_is_refused() public {
        _deposited(500e6);
        bytes32 d = bridge.withdrawalDigest(1, user, 100e6, 0);
        bytes[] memory s = _sigs(d, _first(3));
        // flip signature 0 to its high-s twin: s' = n - s, v' = 55 - v
        (bytes32 r, bytes32 ss, uint8 v) = _split(s[0]);
        uint256 n = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141;
        s[0] = abi.encodePacked(r, bytes32(n - uint256(ss)), uint8(55 - v));
        vm.expectRevert(TorihikiBridge.BadSignature.selector);
        bridge.requestWithdrawal(1, user, 100e6, s);
    }

    function _split(bytes memory sig) internal pure returns (bytes32 r, bytes32 s, uint8 v) {
        assembly {
            r := mload(add(sig, 32))
            s := mload(add(sig, 64))
            v := byte(0, mload(add(sig, 96)))
        }
    }

    // ── the locker ─────────────────────────────────────────────────────────────

    function test_one_validator_can_pause_and_only_a_quorum_can_resume() public {
        _deposited(500e6);
        bridge.requestWithdrawal(1, user, 100e6, _sigs(bridge.withdrawalDigest(1, user, 100e6, 0), _first(3)));
        vm.prank(vm.addr(pks[3]));
        bridge.pause();
        vm.warp(block.timestamp + DISPUTE);
        vm.expectRevert(TorihikiBridge.IsPaused.selector);
        bridge.finalizeWithdrawal(1);
        // a stranger cannot pause
        vm.prank(address(0xD00D));
        vm.expectRevert(TorihikiBridge.NotASigner.selector);
        bridge.pause();
        // 1/2 cannot resume
        bytes[] memory half = _sigs(bridge.actionDigest("unpause", 0, 0, 0), _first(2));
        vm.expectRevert(TorihikiBridge.NoQuorum.selector);
        bridge.unpause(half);
        // invalidate the request, then resume
        bridge.invalidateWithdrawal(1, _sigs(bridge.actionDigest("invalidate", 1, 0, 0), _first(3)));
        // Nonces are per kind: the invalidate did not consume unpause's.
        bridge.unpause(_sigs(bridge.actionDigest("unpause", 0, 0, 0), _first(3)));
        vm.expectRevert(TorihikiBridge.AlreadyDone.selector);
        bridge.finalizeWithdrawal(1);
        _eq(usdc.balanceOf(address(bridge)), 500e6, "nothing left the escrow");
    }

    function test_action_signatures_do_not_replay() public {
        bytes[] memory s = _sigs(bridge.actionDigest("unpause", 0, 0, 0), _first(3));
        vm.prank(vm.addr(pks[0]));
        bridge.pause();
        bridge.unpause(s);
        vm.warp(block.timestamp + 2 * DISPUTE);
        vm.prank(vm.addr(pks[0]));
        bridge.pause();
        // The nonce moved, so the old signatures recover to strangers.
        vm.expectRevert();
        bridge.unpause(s);
    }

    // ── the validator set ──────────────────────────────────────────────────────

    function test_the_set_names_its_successor_after_the_dispute_period() public {
        uint256[] memory next = _sortedKeys(5, 0xB0B);
        address[] memory a = _addrs(next);
        uint64[] memory p = _powers(5);
        bytes32 d = bridge.validatorSetDigest(1, a, p);
        vm.expectRevert(TorihikiBridge.NoQuorum.selector);
        bridge.requestValidatorSet(1, a, p, _sigs(d, _first(2)));
        bridge.requestValidatorSet(1, a, p, _sigs(d, _first(3)));
        vm.expectRevert(TorihikiBridge.DisputeWindow.selector);
        bridge.finalizeValidatorSet();
        vm.warp(block.timestamp + DISPUTE);
        bridge.finalizeValidatorSet();
        _eq(bridge.epoch(), 1, "epoch");
        _eq(bridge.totalPower(), 500, "power");
        _eq(bridge.powerOf(vm.addr(pks[0])), 0, "old signer still has power");

        // the old set can no longer sign; the new one can
        _deposited(500e6);
        bytes32 w = bridge.withdrawalDigest(1, user, 1e6, 1);
        vm.expectRevert(TorihikiBridge.NoQuorum.selector);
        bridge.requestWithdrawal(1, user, 1e6, _sigs(w, _first(3)));
        uint256[] memory four = new uint256[](4);
        for (uint256 i = 0; i < 4; i++) {
            four[i] = next[i];
        }
        bridge.requestWithdrawal(1, user, 1e6, _sigs(w, four));
    }

    function test_a_set_smaller_than_four_or_unsorted_is_refused() public {
        uint256[] memory three = _sortedKeys(3, 0xC0C);
        address[] memory a = _addrs(three);
        vm.expectRevert(TorihikiBridge.BadSet.selector);
        bridge.requestValidatorSet(1, a, _powers(3), new bytes[](0));
        uint256[] memory four = _sortedKeys(4, 0xC0C);
        address[] memory b = _addrs(four);
        (b[0], b[1]) = (b[1], b[0]);
        vm.expectRevert(TorihikiBridge.SignersNotSorted.selector);
        bridge.requestValidatorSet(1, b, _powers(4), new bytes[](0));
    }

    function test_epochs_only_move_forward() public {
        uint256[] memory next = _sortedKeys(4, 0xB0B);
        address[] memory a = _addrs(next);
        vm.expectRevert(TorihikiBridge.StaleEpoch.selector);
        bridge.requestValidatorSet(0, a, _powers(4), new bytes[](0));
    }

    function test_signatures_are_bound_to_the_torihiki_chain() public {
        TorihikiBridge other = new TorihikiBridge(
            IERC20(address(usdc)), "torihiki-devnet-1", DISPUTE, 1_000_000e6, _addrs(_first(4)), _powers(4)
        );
        require(bridge.withdrawalDigest(1, user, 1, 0) != other.withdrawalDigest(1, user, 1, 0), "same digest");
    }

    // ── fixed vectors shared with the engine (torihiki.bridge) ─────────────────

    /// The same digests are pinned in `test/torihiki/bridge_test.cljk` and were
    /// produced independently with `cast` (see that test). Three implementations,
    /// one number each: if the engine and the contract disagreed, validators
    /// would sign withdrawals the bridge refuses.
    function test_digest_vectors_match_the_engine() public {
        vm.chainId(8453);
        TorihikiBridge b = new TorihikiBridge(
            IERC20(address(usdc)), "torihiki-1", DISPUTE, 1_000_000e6, _addrs(_first(4)), _powers(4)
        );
        address at = address(0x00000000000000000000000000000000000B1d6E);
        vm.etch(at, address(b).code);
        TorihikiBridge fixed_ = TorihikiBridge(at);
        require(
            fixed_.domainSeparator() == 0x86e8580fdc999a14880ff08ec058559fd4b94b6876f91547e09b22448b6b5e9f,
            "domain"
        );
        require(
            fixed_.withdrawalDigest(42, address(0x1111111111111111111111111111111111111111), 123456789, 3)
                == 0x8c963d59eb6fe8d941f8f833d737fada5ad2105d1b2cbff97696cdffc1e03223,
            "withdrawal digest"
        );
        address[] memory s = new address[](4);
        s[0] = address(0x1000000000000000000000000000000000000001);
        s[1] = address(0x2000000000000000000000000000000000000002);
        s[2] = address(0x3000000000000000000000000000000000000003);
        s[3] = address(0x4000000000000000000000000000000000000004);
        uint64[] memory p = new uint64[](4);
        (p[0], p[1], p[2], p[3]) = (100, 100, 100, 250);
        require(
            fixed_.validatorSetDigest(7, s, p) == 0x5c271e23b0968b0dce1af42f37b5aaa69f5383a4b144a00216182b4b68b5b4a8,
            "validator set digest"
        );
        require(
            fixed_.actionDigest("invalidate", 42, 0, 3) == 0x7c224465a85793a4db63475c8766f9152f4f56fd8d618c615bc64a70a5c1ee3f,
            "action digest"
        );
    }

    // ── review fixes ───────────────────────────────────────────────────────────

    function test_an_unrequested_claim_can_be_burned_and_never_requested() public {
        _deposited(500e6);
        bridge.invalidateWithdrawal(5, _sigs(bridge.actionDigest("invalidate", 5, 0, 0), _first(3)));
        bytes[] memory s = _sigs(bridge.withdrawalDigest(5, user, 1e6, 0), _first(3));
        vm.expectRevert(TorihikiBridge.AlreadyRequested.selector);
        bridge.requestWithdrawal(5, user, 1e6, s);
    }

    function test_an_account_the_engine_cannot_represent_is_refused() public {
        vm.prank(user);
        vm.expectRevert(TorihikiBridge.BadAccount.selector);
        bridge.deposit(uint64(2 ** 53), 1);
    }

    function test_a_quorum_unpause_outlasts_a_faulty_locker() public {
        uint256[] memory next = _sortedKeys(4, 0xB0B);
        address[] memory a = _addrs(next);
        uint64[] memory p = _powers(4);
        bridge.requestValidatorSet(1, a, p, _sigs(bridge.validatorSetDigest(1, a, p), _first(3)));
        // the locker pauses; the quorum unpauses
        vm.prank(vm.addr(pks[3]));
        bridge.pause();
        vm.warp(block.timestamp + DISPUTE);
        bridge.unpause(_sigs(bridge.actionDigest("unpause", 0, 0, 0), _first(3)));
        // the locker cannot pause straight back
        vm.prank(vm.addr(pks[3]));
        vm.expectRevert(TorihikiBridge.PauseCooldown.selector);
        bridge.pause();
        // time spent paused did not count toward the set's dispute window
        vm.expectRevert(TorihikiBridge.DisputeWindow.selector);
        bridge.finalizeValidatorSet();
        vm.warp(block.timestamp + DISPUTE);
        bridge.finalizeValidatorSet();
        _eq(bridge.epoch(), 1, "rotated past the locker");
    }

    function test_a_pending_set_can_be_cancelled() public {
        uint256[] memory next = _sortedKeys(4, 0xB0B);
        address[] memory a = _addrs(next);
        uint64[] memory p = _powers(4);
        bridge.requestValidatorSet(1, a, p, _sigs(bridge.validatorSetDigest(1, a, p), _first(3)));
        bridge.cancelValidatorSet(_sigs(bridge.actionDigest("cancel-set", 1, 0, 0), _first(3)));
        vm.warp(block.timestamp + DISPUTE);
        vm.expectRevert(TorihikiBridge.NotRequested.selector);
        bridge.finalizeValidatorSet();
    }

    function test_a_set_proposal_signed_for_an_older_set_does_not_verify() public {
        // rotate once
        uint256[] memory next = _sortedKeys(4, 0xB0B);
        address[] memory a = _addrs(next);
        uint64[] memory p = _powers(4);
        uint256[] memory third = _sortedKeys(4, 0xD0D);
        address[] memory b = _addrs(third);
        // the current set signs a proposal for epoch 2 now, while it is epoch 0
        bytes[] memory stale = _sigs(bridge.validatorSetDigest(2, b, p), _first(3));
        bridge.requestValidatorSet(1, a, p, _sigs(bridge.validatorSetDigest(1, a, p), _first(3)));
        vm.warp(block.timestamp + DISPUTE);
        bridge.finalizeValidatorSet();
        vm.expectRevert();
        bridge.requestValidatorSet(2, b, p, stale);
    }
}
