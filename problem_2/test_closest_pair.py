"""Run:  python test_closest_pair.py   (or: pytest)"""
import random
from closest_pair import brute_force, divide_and_conquer, agree, verify


def test_less_than_two():
    assert brute_force([]) is None and divide_and_conquer([]) is None
    assert brute_force([(1, 1)]) is None and divide_and_conquer([(1, 1)]) is None


def test_two_points():
    assert divide_and_conquer([(0, 0), (3, 4)])[0] == 5.0


def test_duplicates():
    r = divide_and_conquer([(1, 1), (5, 5), (1, 1), (9, 2)])
    assert r[0] == 0.0 and r[1] == r[2] == (1, 1)


def test_collinear_same_x_same_y():
    assert divide_and_conquer([(i, 7) for i in range(100)])[0] == 1.0
    assert divide_and_conquer([(7, i * 2) for i in range(100)])[0] == 2.0


def test_negative_and_float():
    pts = [(-1.5, -2.5), (-1.5, -2.0), (10, 10)]
    assert abs(divide_and_conquer(pts)[0] - 0.5) < 1e-12


def test_very_small_distances():
    pts = [(0.0, 0.0), (1e-200, 0.0), (1.0, 1.0)]
    assert brute_force(pts)[0] == 1e-200
    assert divide_and_conquer(pts)[0] == 1e-200


def test_random_large():
    random.seed(5)
    for n in (4, 5, 50, 500, 2000):
        pts = [(random.randint(0, 50), random.randint(0, 50)) for _ in range(n)]
        assert agree(brute_force(pts), divide_and_conquer(pts))


def test_stress():
    assert verify(1000)


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn(); print("PASS", name)
