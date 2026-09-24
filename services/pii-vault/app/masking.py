"""Display helpers for sensitive identifiers."""

import re

_SSN_PATTERN = re.compile(r"^(\d{3})-(\d{2})-(\d{4})$")


def mask_ssn(ssn: str) -> str:
    """Return the SSN with everything except the last four digits hidden."""
    return _SSN_PATTERN.sub(r"***-**-\3", ssn)


def ssn_last4(ssn: str) -> str:
    """Return the last four digits of the SSN, used for identity verification."""
    digits = re.sub(r"\D", "", ssn)
    return f"{int(digits[-4:]):04d}"
