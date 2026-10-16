package com.umc.pediupatrao.entity;

public enum Perfil {
    ADMIN, GERENTE, ATENDENTE;

    public static Perfil de(String valor) {
        if (valor == null) return null;
        try {
            return Perfil.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}