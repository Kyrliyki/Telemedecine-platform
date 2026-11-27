"""Shared FastAPI web conventions."""

from .cors import install_cors
from .problem import install_problem_details

__all__ = ["install_cors", "install_problem_details"]
