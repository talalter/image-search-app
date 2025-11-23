# exception_handlers.py
"""
Global exception handlers for FastAPI application.
Similar to Spring Boot's @ControllerAdvice, these handlers catch exceptions
and convert them to appropriate HTTP responses.
"""

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from exceptions import (
    DatabaseException,
    DuplicateRecordException,
    RecordNotFoundException,
    DatabaseOperationException,
    InvalidInputException,
    InvalidCredentialsException,
    StorageException
)
from logging_config import get_logger

logger = get_logger(__name__)


def register_exception_handlers(app: FastAPI):
    """
    Register all custom exception handlers with the FastAPI app.

    Args:
        app: FastAPI application instance
    """

    @app.exception_handler(DuplicateRecordException)
    async def duplicate_record_handler(request: Request, exc: DuplicateRecordException):
        """Handle duplicate record exceptions (e.g., username already exists)"""
        logger.warning(f"Duplicate record: {exc.message}", extra=exc.details)
        return JSONResponse(
            status_code=409,  # Conflict
            content={
                "error": "Duplicate Record",
                "message": exc.message,
                "details": exc.details
            }
        )

    @app.exception_handler(RecordNotFoundException)
    async def record_not_found_handler(request: Request, exc: RecordNotFoundException):
        """Handle record not found exceptions"""
        logger.info(f"Record not found: {exc.message}", extra=exc.details)
        return JSONResponse(
            status_code=404,  # Not Found
            content={
                "error": "Not Found",
                "message": exc.message,
                "details": exc.details
            }
        )

    @app.exception_handler(InvalidInputException)
    async def invalid_input_handler(request: Request, exc: InvalidInputException):
        """Handle invalid input exceptions"""
        logger.warning(f"Invalid input: {exc.message}", extra={"field": exc.field_name})
        return JSONResponse(
            status_code=400,  # Bad Request
            content={
                "error": "Invalid Input",
                "message": exc.message,
                "field": exc.field_name,
                "details": exc.details
            }
        )

    @app.exception_handler(DatabaseOperationException)
    async def database_operation_handler(request: Request, exc: DatabaseOperationException):
        """Handle general database operation failures"""
        logger.error(f"Database operation failed: {exc.message}", extra=exc.details, exc_info=True)
        return JSONResponse(
            status_code=500,  # Internal Server Error
            content={
                "error": "Database Error",
                "message": "An error occurred while processing your request",
                "details": exc.details if logger.level <= 10 else {}  # Only show details in debug mode
            }
        )

    @app.exception_handler(DatabaseException)
    async def database_exception_handler(request: Request, exc: DatabaseException):
        """Catch-all for any other database exceptions"""
        logger.error(f"Database exception: {exc.message}", extra=exc.details, exc_info=True)
        return JSONResponse(
            status_code=500,
            content={
                "error": "Database Error",
                "message": "An unexpected database error occurred"
            }
        )

    @app.exception_handler(InvalidCredentialsException)
    async def invalid_credentials_handler(request: Request, exc: InvalidCredentialsException):
        """Handle invalid login credentials"""
        logger.info("Invalid login attempt")
        return JSONResponse(
            status_code=401,  # Unauthorized
            content={
                "error": "Authentication Failed",
                "message": exc.message
            }
        )

    @app.exception_handler(StorageException)
    async def storage_exception_handler(request: Request, exc: StorageException):
        """Handle file storage errors"""
        logger.error(f"Storage error: {exc.message}", extra=exc.details, exc_info=True)
        return JSONResponse(
            status_code=500,
            content={
                "error": "Storage Error",
                "message": "Failed to process file storage operation",
                "details": exc.details if logger.level <= 10 else {}
            }
        )

    logger.info("Exception handlers registered successfully")
