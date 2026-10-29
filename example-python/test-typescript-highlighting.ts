// Test file for TypeScript highlighting
// Generated to verify TypeScript tokenizer with generics and advanced features

// Generics example
interface Container<T> {
    value: T;
    getValue(): T;
    setValue(v: T): void;
}

// Type aliases
type ID = string | number;
type User = {
    id: ID;
    name: string;
    email: string;
};

// Enum example
enum Status {
    Active = "ACTIVE",
    Inactive = "INACTIVE",
    Pending = "PENDING"
}

// Abstract class
abstract class BaseService<T> {
    protected items: T[] = [];

    abstract process(item: T): T;

    public add(item: T): void {
        this.items.push(item);
    }
}

// Interface implementation with generics
class UserService extends BaseService<User> implements Container<User> {
    private value: User;

    constructor(user: User) {
        super();
        this.value = user;
    }

    getValue(): User {
        return this.value;
    }

    setValue(user: User): void {
        this.value = user;
    }

    process(user: User): User {
        return {
            ...user,
            email: user.email.toLowerCase()
        };
    }
}

// Arrow functions with type inference
const mapUsers = (users: User[]): Map<ID, User> => {
    const map = new Map<ID, User>();
    users.forEach(user => {
        map.set(user.id, user);
    });
    return map;
};

// Async/await with generics
async function fetchUser(id: ID): Promise<User | null> {
    try {
        const response = await fetch(`/api/users/${id}`);
        if (!response.ok) return null;
        const data: User = await response.json();
        return data;
    } catch (error) {
        console.error("Failed to fetch user:", error);
        return null;
    }
}

// Decorators (TypeScript specific)
@Deprecated("Use fetchUserAsync instead")
function getUser(id: ID): User | undefined {
    return undefined;
}

// Template strings
const userGreeting = `Hello, ${new UserService({
    id: 1,
    name: "John",
    email: "john@example.com"
}).getValue().name}!`;

// Keyof type for type-safe property access
function getProperty<T extends object, K extends keyof T>(obj: T, key: K): T[K] {
    return obj[key];
}

// Conditional types
type IsString<T> = T extends string ? true : false;
type A = IsString<"hello">;  // true
type B = IsString<123>;       // false

// Union and intersection types
type Admin = { role: "admin"; permissions: string[] };
type User2 = { id: number; name: string };
type AdminUser = Admin & User2;

export { Container, UserService, User, Status };
export default UserService;
