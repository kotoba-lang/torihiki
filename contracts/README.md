# torihiki bridge contract

`src/TorihikiBridge.sol` — escrow for torihiki collateral, controlled by
torihiki's validator set (roadmap D3). See the repository README, "The bridge".

```bash
forge test
```

Unaudited. Do not deploy with value before an external audit; start with a
small `depositCap`.
