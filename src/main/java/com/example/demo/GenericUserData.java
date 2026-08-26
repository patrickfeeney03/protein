package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class GenericUserData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String json;

    public GenericUserData(String json) {
        this.json = json;
    }


    public GenericUserData() {
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
