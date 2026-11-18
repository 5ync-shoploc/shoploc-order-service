package com.shoploc.shoplocorderservice.entity;

/** Statuts d'une commande, tels que définis dans le MCD. */
public enum OrderStatus {
    /** Panier en cours : la commande n'est pas encore validée par le client. */
    PANIER,
    /** Commande passée par le client, en attente de traitement par le commerçant. */
    EN_ATTENTE,
    VALIDEE,
    PRETE,
    RETIREE,
    ANNULEE
}
