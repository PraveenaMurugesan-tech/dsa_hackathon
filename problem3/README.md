# Budget Traveller with Coupons

    pip install -r requirements.txt
    python main.py --mode 1 < sample_input.txt            # Option 1 (stdin)
    python main.py --mode 2 --csv sample_graph.csv        # Option 2 (CSV; first line "K,C" or use --k --c, --n)
    python main.py --mode 3 --n 10 --k 3 --c 2 --seed 1   # Option 3 (generate graph -> graph_generated.csv)
    python tests.py                                       # brute-force cross-check (3000 random cases)

Flags: `--out result.csv --gif journey.gif --png path.png --no-anim --show`

Outputs: cost on stdout (-1 if impossible); `result.csv` (columns on_path / coupon_used = `x`, price_paid);
`journey.gif` animation (running cost, stops used, gold coupon flights with `orig -> discounted`), `path.png`.

Algorithm: layered DP over (flights used, coupons used, city) -> O(min(K+1,N-1) * min(C,K+1) * M),
exact optimum. "K stops" = at most K+1 flights; coupon price = p // 2.
Handled: N=1 (cost 0), parallel/opposite edges, self loops, zero prices, K=0, C=0, unreachable, bad input.
