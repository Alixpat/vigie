package com.alixpat.vigie.model;

public class WeatherData {

    private final String cityName;
    private final double temperature;
    private final int weatherCode;
    private final long lastUpdate;

    public WeatherData(String cityName, double temperature, int weatherCode) {
        this.cityName = cityName;
        this.temperature = temperature;
        this.weatherCode = weatherCode;
        this.lastUpdate = System.currentTimeMillis();
    }

    public String getCityName() { return cityName; }
    public double getTemperature() { return temperature; }
    public int getWeatherCode() { return weatherCode; }
    public long getLastUpdate() { return lastUpdate; }

    public String getWeatherDescription() {
        switch (weatherCode) {
            case 0: return "Ciel dégagé";
            case 1: return "Principalement dégagé";
            case 2: return "Partiellement nuageux";
            case 3: return "Couvert";
            case 45: case 48: return "Brouillard";
            case 51: case 53: case 55: return "Bruine";
            case 56: case 57: return "Bruine verglaçante";
            case 61: case 63: case 65: return "Pluie";
            case 66: case 67: return "Pluie verglaçante";
            case 71: case 73: case 75: return "Neige";
            case 77: return "Grains de neige";
            case 80: case 81: case 82: return "Averses de pluie";
            case 85: case 86: return "Averses de neige";
            case 95: return "Orage";
            case 96: case 99: return "Orage avec grêle";
            default: return "Inconnu";
        }
    }

    public String getWeatherEmoji() {
        switch (weatherCode) {
            case 0: return "\u2600\uFE0F";
            case 1: return "\uD83C\uDF24\uFE0F";
            case 2: return "\u26C5";
            case 3: return "\u2601\uFE0F";
            case 45: case 48: return "\uD83C\uDF2B\uFE0F";
            case 51: case 53: case 55: return "\uD83C\uDF26\uFE0F";
            case 56: case 57: return "\uD83C\uDF28\uFE0F";
            case 61: case 63: case 65: return "\uD83C\uDF27\uFE0F";
            case 66: case 67: return "\uD83C\uDF28\uFE0F";
            case 71: case 73: case 75: case 77: return "\u2744\uFE0F";
            case 80: case 81: case 82: return "\uD83C\uDF26\uFE0F";
            case 85: case 86: return "\uD83C\uDF28\uFE0F";
            case 95: case 96: case 99: return "\u26A1";
            default: return "\u2753";
        }
    }
}
