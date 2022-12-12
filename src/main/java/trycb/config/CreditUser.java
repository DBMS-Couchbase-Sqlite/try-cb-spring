package trycb.config;

import com.couchbase.client.java.json.JsonObject;

public class CreditUser {
    private String id;
    private String name;
    private String surname;
    private int credits;

    public CreditUser(){}

    public CreditUser(String id, String name, String surname, int credits) {
        this.id = id;
        this.name = name;
        this.surname = surname;
        this.credits = credits;
    }

    public CreditUser(JsonObject object) {
        JsonObject userProfile = (JsonObject) object.get("user_profile");

        this.id = userProfile.getString("id");
        this.name = userProfile.getString("name");
        this.surname = userProfile.getString("surname");
        this.credits = userProfile.getInt("credits");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }
}
