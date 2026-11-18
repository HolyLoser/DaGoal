package com.stipasay.dagoal;

public class ShopItem {
    private int id;
    private String name;
    private int price;
    private String category;
    private String resName;
    private String rarityTier;
    private int requiredLevel;
    private String iconEmoji;

    public ShopItem(int id, String name, int price, String category, String resName) {
        this(id, name, price, category, resName, "COMMON", 1, "\uD83D\uDC55");
    }

    public ShopItem(int id, String name, int price, String category, String resName, String rarityTier, int requiredLevel, String iconEmoji) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.resName = resName;
        this.rarityTier = rarityTier;
        this.requiredLevel = requiredLevel;
        this.iconEmoji = iconEmoji;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getPrice() { return price; }
    public String getCategory() { return category; }
    public String getResName() { return resName; }
    public String getRarityTier() { return rarityTier; }
    public int getRequiredLevel() { return requiredLevel; }
    public String getIconEmoji() { return iconEmoji; }
}