"""Display helpers for sensitive identifiers."""

import re

_NON_DIGITS = re.compile(r"\D")

MASKED_SSN = "***-**-****"


def ssn_last4(ssn: str) -> str:
    """Return the last four digits of the SSN, used for identity verification.

    Returns an empty string when the value holds fewer than four digits.
    """
    digits = _NON_DIGITS.sub("", ssn)
    return digits[-4:] if len(digits) >= 4 else ""


def mask_ssn(ssn: str) -> str:
    """Return the SSN with everything except the last four digits hidden."""
    last4 = ssn_last4(ssn)
    return f"***-**-{last4}" if last4 else MASKED_SSN
