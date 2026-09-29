package com.aceos.model;

public class User {

    private int userId;
    private String name;
    private String email;
    private UserRole role;

    public User(String name, String email, UserRole role) {
        this(0, name, email, role);
    }

    public User(int userId, String name, String email, UserRole role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }
}
