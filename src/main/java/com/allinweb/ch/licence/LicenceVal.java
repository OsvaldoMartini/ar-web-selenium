package com.allinweb.ch.licence;

public enum LicenceVal {
    // Definisci le possibili costanti per l'enumerazione
    VALID,
    EXPIRED,
    REVOKED,
    PENDING_ACTIVATION,
    MISSING,
    PCMISSMATCH,
    PCNOTMATCH,
    DOMAINNOTMATCH,
    USRNOTMATCH;

    // Metodo di esempio per ottenere una descrizione umana leggibile delle costanti
    @Override
    public String toString() {
        switch (this) {
            case VALID:
                return "Licenza valida";
            case EXPIRED:
                return "Licenza scaduta";
            case REVOKED:
                return "Licenza revocata";
            case PENDING_ACTIVATION:
                return "Licenza in attesa di attivazione";
            case MISSING:
                return "Licenza mancante";
            case PCMISSMATCH:
                return "Licenza non valida per tale PC";
            case PCNOTMATCH:
                return "Licenza non valida per tale PC";
            case DOMAINNOTMATCH:
                return "Licenza no valida per il dominio di questo PC";
            case USRNOTMATCH:
                return "Licenza non valida per tale utente su questo PC";
            default:
                return "Stato della licenza non definito";
        }
    }

    // Metodo per verificare se la licenza  attiva
    public boolean isActive() {
        return this == VALID /*|| this == PENDING_ACTIVATION*/;
    }

    public boolean isExpired() {
        return this == EXPIRED;
    }

    public boolean isMissing() {
        return this == MISSING;
    }

    public String getStaus() {
        return this.toString();
    }
}
