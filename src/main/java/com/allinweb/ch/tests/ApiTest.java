package com.allinweb.ch.tests;

import java.util.List;

public class ApiTest {
    public static void main(String[] args) {
        // Create a list of CountryData
        List<CountryData> countries =
                List.of(new CountryData("US", "United States", "USA"), new CountryData("CA", "Canada", "CAN"));

        // Create an ApiResponse instance with CountryData
        ApiResponse<CountryData> response = new ApiResponse<>(countries, "Success", 200);

        // Cast data to List<CountryData> and process it
        if (!response.data().isEmpty() && response.data().get(0) instanceof CountryData) {
            List<CountryData> countryDataList = (List<CountryData>) response.data();
            countryDataList.forEach(country -> System.out.printf(
                    "Code: %s, Name: %s, ISO3: %s%n",
                    country.countrycd(), country.countryName(), country.countryIso3()));
        } else {
            System.out.println("The data is not of type CountryData.");
        }
    }
}
