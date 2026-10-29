"""Test file for Python highlighting - comprehensive language features"""

from typing import Generic, TypeVar, Dict, List, Optional
from dataclasses import dataclass
from abc import ABC, abstractmethod
import asyncio
import json

# Type variables for generic programming
T = TypeVar('T')
K = TypeVar('K')

class Container(Generic[T]):
    """Generic container class to test type annotations"""
    
    def __init__(self, value: T) -> None:
        self.value = value
    
    def get_value(self) -> T:
        return self.value
    
    def set_value(self, value: T) -> None:
        self.value = value


@dataclass
class User:
    """User dataclass with type hints"""
    id: int
    name: str
    email: str
    status: str = "active"


class Status(ABC):
    """Abstract base class for status handling"""
    
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"
    PENDING = "PENDING"
    
    @abstractmethod
    def validate(self) -> bool:
        pass


class UserService(Container[User]):
    """Service class handling user operations"""
    
    def __init__(self, user: User):
        super().__init__(user)
        self._cache: Dict[int, User] = {}
    
    def process(self, user: User) -> User:
        """Process user data"""
        return User(
            id=user.id,
            name=user.name.lower(),
            email=user.email.lower(),
            status=user.status
        )
    
    def get_user_by_id(self, user_id: int) -> Optional[User]:
        """Retrieve user from cache or return None"""
        return self._cache.get(user_id)
    
    def add_users(self, users: List[User]) -> Dict[int, User]:
        """Add multiple users to cache"""
        result: Dict[int, User] = {}
        for user in users:
            self._cache[user.id] = user
            result[user.id] = user
        return result


async def fetch_user(user_id: int) -> Optional[User]:
    """Async function to fetch user data"""
    try:
        # Simulate API call
        await asyncio.sleep(0.1)
        data = {
            "id": user_id,
            "name": "John Doe",
            "email": "john@example.com",
            "status": "active"
        }
        return User(**data)
    except Exception as error:
        print(f"Error fetching user: {error}")
        return None


def map_users(users: List[User]) -> Dict[int, User]:
    """Map users by ID"""
    return {user.id: user for user in users}


# Decorators example
def deprecated(func):
    """Decorator to mark functions as deprecated"""
    def wrapper(*args, **kwargs):
        print(f"Warning: {func.__name__} is deprecated")
        return func(*args, **kwargs)
    return wrapper


@deprecated
def old_get_user(user_id: int) -> Optional[User]:
    """Old function marked as deprecated"""
    return None


# String formatting
user_name = "Alice"
greeting = f"Hello, {user_name}!"
template = f"""
User Information:
Name: {user_name}
Status: active
"""

# List comprehensions with type hints
user_ids: List[int] = [user.id for user in [
    User(1, "Alice", "alice@example.com"),
    User(2, "Bob", "bob@example.com"),
    User(3, "Charlie", "charlie@example.com")
]]

# Dictionary comprehensions
user_emails: Dict[int, str] = {
    user.id: user.email
    for user in [
        User(1, "Alice", "alice@example.com"),
        User(2, "Bob", "bob@example.com"),
    ]
}

# Lambda functions
sort_users = lambda users: sorted(users, key=lambda u: u.name)

# Context managers
class UserContext:
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        pass


# Main execution
if __name__ == "__main__":
    # Create service and test
    user = User(1, "John", "john@example.com")
    service = UserService(user)
    
    # Process user
    processed = service.process(user)
    print(f"Processed user: {processed}")
    
    # Async operation
    result = asyncio.run(fetch_user(1))
    print(f"Fetched user: {result}")

__all__ = ["UserService", "User", "Status", "fetch_user"]
