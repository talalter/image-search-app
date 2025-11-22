# exceptions.py
"""
Custom exception hierarchy for the application.
Similar to Spring Boot's exception structure with custom exceptions for different error scenarios.
"""

class DatabaseException(Exception):
    """Base exception for all database-related errors."""
    def __init__(self, message: str, details: dict = None, original_exception: Exception = None):
        super().__init__(message)
        self.message = message
        self.details = details or {}
        self.original_exception = original_exception


class DuplicateRecordException(DatabaseException):
    """Raised when attempting to create a record that already exists (e.g., duplicate username)."""
    def __init__(self, resource_type: str, field_name: str, field_value: str, original_exception: Exception = None):
        message = f"{resource_type} with {field_name}='{field_value}' already exists"
        super().__init__(
            message=message,
            details={"resource_type": resource_type, "field_name": field_name, "field_value": field_value},
            original_exception=original_exception
        )


class RecordNotFoundException(DatabaseException):
    """Raised when a requested record cannot be found in the database."""
    def __init__(self, resource_type: str, identifier: any, original_exception: Exception = None):
        message = f"{resource_type} with identifier '{identifier}' not found"
        super().__init__(
            message=message,
            details={"resource_type": resource_type, "identifier": identifier},
            original_exception=original_exception
        )


class DatabaseOperationException(DatabaseException):
    """Raised when a database operation fails unexpectedly."""
    pass


class InvalidInputException(Exception):
    """Raised when user input is invalid or doesn't meet requirements."""
    def __init__(self, field_name: str, message: str, details: dict = None):
        super().__init__(message)
        self.field_name = field_name
        self.message = message
        self.details = details or {}


class InvalidCredentialsException(Exception):
    """Raised when login credentials are invalid."""
    def __init__(self, message: str = "Invalid username or password"):
        super().__init__(message)
        self.message = message


class StorageException(Exception):
    """Raised when file storage operations fail."""
    def __init__(self, message: str, details: dict = None, original_exception: Exception = None):
        super().__init__(message)
        self.message = message
        self.details = details or {}
        self.original_exception = original_exception
