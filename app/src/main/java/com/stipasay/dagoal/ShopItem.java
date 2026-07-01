package com.stipasay.dagoal;

public class ShopItem {
    private int id;
    private String name;
    private int price;
    private String category;
    private String resName;

    public ShopItem(int id, String name, int price, String category, String resName) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.resName = resName;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getPrice() { return price; }
    public String getCategory() { return category; }
    public String getResName() { return resName; }
}