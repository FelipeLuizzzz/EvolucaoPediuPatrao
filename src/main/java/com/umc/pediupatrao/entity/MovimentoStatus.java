package com.umc.pediupatrao.entity;

import java.time.Instant;

public record MovimentoStatus(String status, Instant dataHora, String usuario) {
}
