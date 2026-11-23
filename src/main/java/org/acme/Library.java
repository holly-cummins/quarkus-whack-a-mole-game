package org.acme;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Library {

    public List<String> titles() {
        return List.of("Dune", "Emma", "Ulysses");
    }
}