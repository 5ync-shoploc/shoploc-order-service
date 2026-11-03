# Shoploc Order Service

## Présentation

`shoploc-order-service` est le microservice backend qui gère le cycle de vie
des commandes de la plateforme Shoploc.

Il gère les paniers, les commandes, les lignes de commande et les différents
statuts d'une commande, de sa création à sa finalisation ou son annulation.

Le service fait partie de l'architecture microservices de Shoploc.

## Responsabilités

Le service commandes est responsable de :

- la gestion des paniers ;
- la création des commandes ;
- la gestion des lignes de commande ;
- la gestion du cycle de vie des commandes ;
- la mise à jour du statut des commandes ;
- l'annulation des commandes ;
- le calcul du total des commandes ;
- la relation entre les commandes et les produits ;
- la mise à disposition des API de commande pour les clients et les autres services Shoploc ;
- l'orchestration des échanges avec les autres services si nécessaire.

Le service commandes contient la logique métier liée aux commandes.

La gestion des produits et des stocks reste de la responsabilité de
`shoploc-catalog-service`.

Le traitement des paiements reste de la responsabilité de
`shoploc-payment-service`.

L'authentification et la gestion des identités restent de la responsabilité de
`shoploc-identity-service`.

## Architecture

```text
                         Clients
                            │
                            ▼
                  ┌─────────────────────┐
                  │   Shoploc API       │
                  │      Gateway        │
                  └──────────┬──────────┘
                             │
                             ▼
                  ┌─────────────────────┐
                  │    Order Service    │
                  │                     │
                  │ Cart                │
                  │ Orders              │
                  │ Order Items         │
                  │ Order Lifecycle     │
                  └───────┬─────┬───────┘
                          │     │
                ┌─────────┘     └─────────┐
                ▼                         ▼
       ┌─────────────────┐       ┌─────────────────┐
       │ Catalog Service │       │ Payment Service │
       │                 │       │                 │
       │ Products/Stock  │       │ Payments        │
       └─────────────────┘       └─────────────────┘
                          │
                          ▼
                  ┌─────────────────┐
                  │    Order DB     │
                  └─────────────────┘
```

## Fonctionnalités réalisées

| User story | Endpoint | Rôle |
|---|---|---|
| Un client commande un ou plusieurs articles avec leur quantité | `POST /api/order` | `CLIENT` |
| Un client consulte la liste de ses commandes | `GET /api/order` | `CLIENT` |
| Un client consulte le détail d'une de ses commandes | `GET /api/order/{orderId}` | `CLIENT` |
| Un commerçant consulte les lignes de commande contenant ses articles | `GET /api/order/merchant/items` | `COMMERCANT` |

Règles métier :

- Chaque article doit exister, être actif et avoir un stock suffisant.
- Le stock est décrémenté à la validation de la commande.
- Le nom, le prix unitaire et le commerçant de l'article sont copiés dans la ligne de commande : le prix reste figé.
- Une nouvelle commande a le statut `PENDING` (EN_ATTENTE).
- Un client ne voit que ses propres commandes. La commande d'un autre client renvoie `404`.
- Un commerçant ne voit que les lignes de commande de ses propres articles.

## API

Les endpoints sont accessibles directement sur le service (`http://localhost:8082`) ou via l'API Gateway (`http://localhost:8080`, route `/api/order/**`).

### Passer une commande

```http
POST /api/order
X-User-Id: client-1
X-User-Role: CLIENT
Content-Type: application/json

{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

Réponse `201 Created`, avec l'en-tête `Location: /api/order/{id}` :

```json
{
  "id": 1,
  "clientId": "client-1",
  "status": "PENDING",
  "totalAmount": 13.80,
  "createdAt": "2026-11-03T19:10:00Z",
  "items": [
    { "productId": 1, "productName": "Pain de campagne", "merchantId": "merchant-1",
      "quantity": 2, "unitPrice": 3.50, "lineTotal": 7.00 },
    { "productId": 3, "productName": "Fromage de chèvre", "merchantId": "merchant-2",
      "quantity": 1, "unitPrice": 6.80, "lineTotal": 6.80 }
  ]
}
```

Si le même article apparaît plusieurs fois dans la requête, les quantités sont additionnées.

### Codes d'erreur

Les erreurs suivent le format standard Problem Details (RFC 9457).

| Code | Cas |
|---|---|
| `400` | Corps invalide : `items` vide, `productId` manquant, `quantity` ≤ 0 |
| `401` | En-tête `X-User-Id` ou `X-User-Role` absent |
| `403` | Rôle incorrect ou inconnu (par exemple un commerçant qui passe une commande) |
| `404` | Commande introuvable, ou appartenant à un autre client |
| `409` | Stock insuffisant |
| `422` | Article inconnu ou inactif |

## Dépendances simulées (temporaire)

Le service est développé et testé indépendamment des autres services. Ses deux dépendances externes sont isolées derrière une interface, avec une implémentation simulée. Les remplacer ne changera ni le controller ni le service.

| Dépendance | Implémentation actuelle | Cible |
|---|---|---|
| Identité (Keycloak) | `CurrentUserArgumentResolver` : lit les en-têtes `X-User-Id` et `X-User-Role` | Lire l'utilisateur et son rôle dans le token JWT Keycloak |
| Catalogue | `InMemoryProductCatalogClient` : produits de démonstration en mémoire | Appels HTTP vers `shoploc-catalog-service` |

> ⚠️ L'identité par en-têtes sert uniquement au développement : n'importe qui peut envoyer un faux en-tête. Elle doit être remplacée par la vérification du token JWT avant toute mise en production.

Produits de démonstration (le stock est réinitialisé à chaque redémarrage du service) :

| id | Produit | Prix | Actif | Stock | Commerçant |
|---|---|---|---|---|---|
| 1 | Pain de campagne | 3.50 | oui | 100 | merchant-1 |
| 2 | Croissant | 1.20 | oui | 5 | merchant-1 |
| 3 | Fromage de chèvre | 6.80 | oui | 20 | merchant-2 |
| 4 | Confiture | 4.00 | **non** | 50 | merchant-2 |

Ce dont le service a besoin du catalogue pour remplacer la simulation :

- pour chaque produit : `id`, `name`, `price`, `active`, `stock`, `merchantId` ;
- un endpoint pour lire un produit ;
- un endpoint qui décrémente le stock de plusieurs produits de façon atomique (tout ou rien) et refuse l'appel si un stock est insuffisant.

## Configuration

La configuration est centralisée dans `shoploc-config-server` (`configurations/shoploc-order-service.yaml`). Ne pas modifier le fichier `application.yaml` local.

| Variable | Valeur par défaut | Rôle |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/shoploc_order_dev` | URL PostgreSQL |
| `DB_USERNAME` | `postgres` | Utilisateur PostgreSQL |
| `DB_PASSWORD` | *(vide)* | Mot de passe PostgreSQL |
| `SPRING_PROFILES_ACTIVE` | — | `local` pour utiliser `application-local.yaml` (non versionné) |

Le service écoute sur le port `8082`.

## Lancer en local

Prérequis : Java 21 ou plus, Docker (pour les tests), un PostgreSQL local avec la base `shoploc_order_dev`, et le fichier `~/.m2/settings.xml` pour GitHub Packages.

Démarrer les applications dans cet ordre :

1. `shoploc-config-server` (port 8888)
2. `shoploc-order-service` (port 8082), par exemple avec `SPRING_PROFILES_ACTIVE=local;DB_USERNAME=<ton_utilisateur>`
3. `shoploc-api-gateway` (port 8080)

Vérifier :

```bash
curl http://localhost:8082/actuator/health   # "status":"UP"
curl http://localhost:8080/actuator/health   # "status":"UP"
```

## Tests

```bash
./mvnw clean verify
```

- Tests unitaires : service (Mockito) et controller (MockMvc en mode standalone).
- Tests de bout en bout (`OrderFlowIntegrationTest`) : HTTP → controller → service → vrai PostgreSQL, lancé par Testcontainers. Docker doit être démarré.
- Le build échoue si la couverture de lignes est inférieure à 80 % (JaCoCo, configuré dans `shoploc-parent`).

### Scénario manuel de bout en bout via la Gateway

```bash
GW=http://localhost:8080
J="Content-Type: application/json"
C1=(-H "X-User-Id: client-1" -H "X-User-Role: CLIENT")
C2=(-H "X-User-Id: client-2" -H "X-User-Role: CLIENT")
M1=(-H "X-User-Id: merchant-1" -H "X-User-Role: COMMERCANT")
M2=(-H "X-User-Id: merchant-2" -H "X-User-Role: COMMERCANT")
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

R=$(curl -s -X POST $GW/api/order -H "$J" "${C1[@]}" -d '{"items":[{"productId":1,"quantity":2},{"productId":3,"quantity":1}]}')
echo "$R"; ID=$(echo "$R" | sed -E 's/^\{"id":([0-9]+).*/\1/')
echo "Stock insuffisant         : $(code -X POST $GW/api/order -H "$J" "${C1[@]}" -d '{"items":[{"productId":2,"quantity":6}]}') (attendu 409)"
echo "Produit inactif           : $(code -X POST $GW/api/order -H "$J" "${C1[@]}" -d '{"items":[{"productId":4,"quantity":1}]}') (attendu 422)"
echo "Commerçant qui commande   : $(code -X POST $GW/api/order -H "$J" "${M1[@]}" -d '{"items":[{"productId":1,"quantity":1}]}') (attendu 403)"
echo "Sans identité             : $(code -X POST $GW/api/order -H "$J" -d '{"items":[{"productId":1,"quantity":1}]}') (attendu 401)"
echo "Ma commande               : $(code $GW/api/order/$ID "${C1[@]}") (attendu 200)"
echo "Commande d'un autre client: $(code $GW/api/order/$ID "${C2[@]}") (attendu 404)"
echo "Lignes de merchant-1      :"; curl -s $GW/api/order/merchant/items "${M1[@]}"; echo
echo "Lignes de merchant-2      :"; curl -s $GW/api/order/merchant/items "${M2[@]}"; echo
```
