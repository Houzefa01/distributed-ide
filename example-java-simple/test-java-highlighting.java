package com.distributed_ide.test;

import java.util.*;
import java.util.stream.*;
import java.util.function.Function;
import java.util.concurrent.*;

/**
 * Test file for Java syntax highlighting
 * Comprehensive test of all Java language features
 */
public class TestJavaHighlighting {

    // Generic type parameters
    public static <T> T getFirst(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    interface Container<T> {
        T getValue();
        void setValue(T value);
    }

    enum Status {
        ACTIVE("ACTIVE", true),
        INACTIVE("INACTIVE", false),
        PENDING("PENDING", null);

        private final String displayName;
        private final Boolean isEnabled;

        Status(String displayName, Boolean isEnabled) {
            this.displayName = displayName;
            this.isEnabled = isEnabled;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @FunctionalInterface
    interface UserProcessor<T> {
        T process(T item);
    }

    @Deprecated(since = "2.0", forRemoval = true)
    public static void oldMethod() {
        System.out.println("This method is deprecated");
    }

    // Inner class
    public static class User implements Comparable<User> {
        private final int id;
        private final String name;
        private final String email;
        private Status status;

        public User(int id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.status = Status.PENDING;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        @Override
        public int compareTo(User other) {
            return this.name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return String.format(
                "User(id=%d, name=%s, email=%s, status=%s)",
                id, name, email, status
            );
        }
    }

    // Generic service class
    public static class UserService<T extends User> implements Container<T> {
        private T value;
        private List<T> users = new ArrayList<>();
        private Map<Integer, T> cache = new ConcurrentHashMap<>();

        public UserService(T user) {
            this.value = user;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public void setValue(T value) {
            this.value = value;
        }

        public void addUser(T user) {
            users.add(user);
            cache.put(user.getId(), user);
        }

        public Optional<T> findUserById(int id) {
            return Optional.ofNullable(cache.get(id));
        }

        public List<T> getAllUsers() {
            return Collections.unmodifiableList(users);
        }

        // Stream operations
        public List<T> findActiveUsers() {
            return users.stream()
                .filter(u -> u.getStatus() == Status.ACTIVE)
                .collect(Collectors.toList());
        }

        // Lambdas and method references
        public void printUsers() {
            users.forEach(System.out::println);
        }
    }

    // Try-with-resources example
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            System.out.println("Resource closed");
        }
    }

    public static void main(String[] args) {
        // Variable declarations
        String text = "Hello, World!";
        int count = 42;
        double ratio = 3.14159;
        boolean enabled = true;
        char c = 'A';

        // String concatenation and operations
        String message = "User: " + text;
        String template = """
            Multi-line string
            Line 2
            Line 3""";

        // Collections and generics
        List<User> users = new ArrayList<>();
        Map<Integer, User> userMap = new HashMap<>();
        Set<String> names = new HashSet<>();

        // Try-catch-finally
        try {
            User user = new User(1, "John Doe", "john@example.com");
            users.add(user);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            System.out.println("Cleanup complete");
        }

        // Try-with-resources
        try (Resource resource = new Resource()) {
            System.out.println("Using resource");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Enhanced for loop
        for (User user : users) {
            System.out.println(user.getName());
        }

        // Traditional for loop
        for (int i = 0; i < users.size(); i++) {
            System.out.println("User " + i + ": " + users.get(i));
        }

        // While and do-while loops
        int index = 0;
        while (index < users.size()) {
            index++;
        }

        do {
            index--;
        } while (index > 0);

        // Switch statement (traditional)
        Status status = Status.ACTIVE;
        switch (status) {
            case ACTIVE:
                System.out.println("User is active");
                break;
            case INACTIVE:
                System.out.println("User is inactive");
                break;
            case PENDING:
                System.out.println("User is pending");
                break;
        }

        // Switch expression (Java 14+)
        String statusMessage = switch (status) {
            case ACTIVE -> "Active";
            case INACTIVE -> "Inactive";
            case PENDING -> "Pending";
        };

        // Streams and functional programming
        var userService = new UserService<>(new User(0, "Admin", "admin@example.com"));
        
        // Lambda functions
        users.stream()
            .map(User::getName)
            .filter(name -> !name.isEmpty())
            .forEach(System.out::println);

        // More stream operations
        List<Integer> ids = users.stream()
            .map(User::getId)
            .sorted()
            .collect(Collectors.toList());

        // Anonymous inner class
        Runnable task = new Runnable() {
            @Override
            public void run() {
                System.out.println("Task executed");
            }
        };

        // Ternary operator
        String permission = enabled ? "ALLOWED" : "DENIED";

        // Null-safe operations
        Optional<User> optionalUser = Optional.ofNullable(null);
        optionalUser
            .map(User::getName)
            .ifPresentOrElse(
                System.out::println,
                () -> System.out.println("No user found")
            );

        // Assertions
        assert count > 0 : "Count must be positive";

        // Annotations
        // (Already shown with @FunctionalInterface, @Override, @Deprecated)

        // Number formats
        int decimal = 100;
        long hex = 0xDEADBEEF;
        long binary = 0b1010;
        long octal = 0755;
        double scientific = 1.23E-4;

        System.out.println("Testing complete!");
    }
}
