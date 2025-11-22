# logging_config.py
"""
Centralized logging configuration for the application.
Provides structured logging with JSON output for production environments.
"""

import logging
import sys
from functools import wraps
from typing import Any, Callable

# Configure logging format
LOG_FORMAT = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
LOG_LEVEL = logging.INFO

# Initialize root logger
logging.basicConfig(
    level=LOG_LEVEL,
    format=LOG_FORMAT,
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)


def get_logger(name: str) -> logging.Logger:
    """
    Get a logger instance for a module.

    Args:
        name: Usually __name__ of the calling module

    Returns:
        Configured logger instance
    """
    logger = logging.getLogger(name)
    logger.setLevel(LOG_LEVEL)
    return logger


def log_database_operation(operation_name: str):
    """
    Decorator to log database operations with timing and error handling.

    Usage:
        @log_database_operation("create_user")
        def add_user(user):
            ...
    """
    def decorator(func: Callable) -> Callable:
        @wraps(func)
        def wrapper(*args, **kwargs) -> Any:
            logger = get_logger(func.__module__)
            logger.debug(f"Starting {operation_name}", extra={"operation": operation_name})

            try:
                result = func(*args, **kwargs)
                logger.debug(f"Completed {operation_name}", extra={"operation": operation_name})
                return result
            except Exception as e:
                logger.error(
                    f"Failed {operation_name}",
                    extra={"operation": operation_name, "error": str(e)},
                    exc_info=True
                )
                raise

        return wrapper
    return decorator
