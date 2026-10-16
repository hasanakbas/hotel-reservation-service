package com.example.hotel.model;

public class Guest {
    private final String id;
    private final String name;
    private final String email;
    private int points;
    private int completedStays;

    public Guest(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public Guest(String id, String name, String email, int points, int completedStays) {
        this(id, name, email);
        this.points = points;
        this.completedStays = completedStays;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public int getPoints() {
        return points;
    }

    public int getCompletedStays() {
        return completedStays;
    }

    public LoyaltyTier tier() {
        return LoyaltyTier.fromPoints(points);
    }

    public void addPoints(int p) {
        this.points += p;
    }

    public void incrementCompletedStays() {
        this.completedStays++;
    }
}
