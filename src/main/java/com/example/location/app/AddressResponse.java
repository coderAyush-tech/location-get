package com.example.location.app;

public class AddressResponse {
    private String display_name;
    private Address address;

    // getters and setters
    public String getDisplay_name() {
        return display_name;
    }
    public void setDisplay_name(String display_name) {
        this.display_name = display_name;
    }

    public Address getAddress() {
        return address;
    }
    public void setAddress(Address address) {
        this.address = address;
    }

    // Inner class for address details
    public static class Address {
        private String road;
        private String city;
        private String state;
        private String country;
        private String postcode;

        // getters and setters
        public String getRoad() { return road; }
        public void setRoad(String road) { this.road = road; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }

        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }
    }
}