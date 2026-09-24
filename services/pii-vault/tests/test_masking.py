"""Tests for app/masking.py. All SSNs are synthetic."""

import pytest

from app.masking import mask_ssn, ssn_last4

FULLY_MASKED = "***-**-****"


# --- mask_ssn: happy path ---------------------------------------------------


def test_mask_ssn_shows_only_last_four_digits():
    assert mask_ssn("123-45-6789") == "***-**-6789"


def test_mask_ssn_preserves_leading_zeros_in_last_four():
    assert mask_ssn("123-45-0042") == "***-**-0042"


def test_mask_ssn_never_returns_raw_value_for_well_formed_input():
    raw = "987-65-4321"
    masked = mask_ssn(raw)
    assert masked != raw
    assert "987" not in masked
    assert "65" not in masked.split("-")[1]


# --- mask_ssn: malformed / boundary inputs ----------------------------------
# Business rule: any input shorter than 4 digits, malformed, or in an
# unexpected format must be fully masked and must never return the raw value.


@pytest.mark.parametrize(
    "raw",
    [
        "123456789",  # digits only, no dashes
        "123 45 6789",  # spaces instead of dashes
        "1234-56-789",  # wrong group lengths
        "123-45-67890",  # too long
        "123-45-678",  # too short
        "12a-45-6789",  # letters
        " 123-45-6789",  # leading whitespace
        "123-45-6789 ",  # trailing whitespace
    ],
)
@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: mask_ssn returns the raw value unchanged when the input "
    "does not match ###-##-####; business rule requires full masking",
)
def test_mask_ssn_fully_masks_malformed_input(raw):
    assert mask_ssn(raw) == FULLY_MASKED


@pytest.mark.parametrize("raw", ["", "1", "12", "123", "---"])
@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: mask_ssn returns inputs shorter than 4 digits unchanged "
    "instead of fully masking them",
)
def test_mask_ssn_fully_masks_short_input(raw):
    assert mask_ssn(raw) == FULLY_MASKED


@pytest.mark.parametrize("raw", ["123456789", "123 45 6789", "1234-56-789"])
@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: mask_ssn echoes the raw SSN back for malformed input, "
    "exposing PII",
)
def test_mask_ssn_never_returns_raw_malformed_value(raw):
    assert mask_ssn(raw) != raw


def test_mask_ssn_does_not_crash_on_malformed_input():
    for raw in ["", "abc", "123456789", "---", "12"]:
        mask_ssn(raw)  # must not raise


# --- ssn_last4: happy path --------------------------------------------------


def test_ssn_last4_returns_last_four_digits():
    assert ssn_last4("123-45-6789") == "6789"


def test_ssn_last4_preserves_leading_zeros():
    assert ssn_last4("123-45-0007") == "0007"


def test_ssn_last4_ignores_non_digit_characters():
    assert ssn_last4("123 45 6789") == "6789"
    assert ssn_last4("123456789") == "6789"


def test_ssn_last4_with_exactly_four_digits():
    assert ssn_last4("4321") == "4321"


# --- ssn_last4: boundary / malformed ----------------------------------------


@pytest.mark.parametrize("raw", ["", "abc", "---", "- -"])
@pytest.mark.xfail(
    strict=True,
    raises=ValueError,
    reason="SUSPECTED BUG: ssn_last4 raises ValueError (int('') ) when the input has "
    "no digits; masking must never crash",
)
def test_ssn_last4_does_not_crash_when_no_digits(raw):
    assert ssn_last4(raw) == "****"


@pytest.mark.parametrize("raw", ["1", "12", "123"])
@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: ssn_last4 zero-pads inputs shorter than 4 digits (e.g. "
    "'12' -> '0012') instead of fully masking them",
)
def test_ssn_last4_fully_masks_inputs_shorter_than_four_digits(raw):
    assert ssn_last4(raw) == "****"
