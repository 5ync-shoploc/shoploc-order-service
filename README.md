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
| Un client ajoute des articles à son panier depuis le catalogue | `POST /api/order/cart/items` | `CLIENT` |
| Un client consulte, modifie et vide son panier | `GET /api/order/cart`, `PUT` / `DELETE /api/order/cart/items/{productId}` | `CLIENT` |
| Un client valide son panier : il devient une commande | `POST /api/order/cart/validate` | `CLIENT` |
| Un client commande directement (sans panier) | `POST /api/order` | `CLIENT` |
| Un client consulte la liste de ses commandes | `GET /api/order` | `CLIENT` |
| Un client consulte le détail d'une de ses commandes | `GET /api/order/{orderId}` | `CLIENT` |
| Un client supprime une commande de sa liste (elle est annulée) | `DELETE /api/order/{orderId}` | `CLIENT` |
| Un commerçant consulte la liste des commandes contenant ses articles | `GET /api/order/merchant/orders` | `COMMERCANT` |
| Un commerçant consulte le contenu d'une commande (ses articles) | `GET /api/order/merchant/orders/{orderId}` | `COMMERCANT` |
| Un commerçant consulte les lignes de commande contenant ses articles | `GET /api/order/merchant/items` | `COMMERCANT` |

Règles métier :

- Chaque article doit exister, être actif et avoir un stock suffisant.
- Le stock est décrémenté à la validation de la commande.
- Le nom, le prix unitaire et le commerçant de l'article sont copiés dans la ligne de commande : le prix reste figé.
- Le panier est une commande au statut `PANIER` (au plus une par client). Il n'est pas visible par les commerçants et ne réserve pas de stock.
- Valider le panier relit chaque article dans le catalogue (existant, actif, stock suffisant), fige les prix, décrémente le stock et passe la commande en `EN_ATTENTE`.
- Une nouvelle commande a le statut `EN_ATTENTE`.
- Le créneau de retrait (`pickupSlot`) est facultatif ; s'il est donné, il doit être dans le futur.
- Un client ne voit que ses propres commandes. La commande d'un autre client renvoie `404`.
- Un commerçant ne voit que les lignes de commande de ses propres articles. Une commande sans aucun de ses articles renvoie `404`.
- Le client peut supprimer une commande tant qu'elle est `EN_ATTENTE` (sinon `409`). Elle n'est pas effacée : elle passe au statut `ANNULEE`, disparaît de la liste du client et reste visible par le commerçant avec ce statut.
- Limite connue : l'annulation ne remet pas encore le stock dans le catalogue (il faudra un endpoint d'incrément côté catalogue).

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
  ],
  "pickupSlot": "2026-11-20T10:30:00"
}
```

Réponse `201 Created`, avec l'en-tête `Location: /api/order/{id}` :

```json
{
  "id": 1,
  "clientId": "client-1",
  "status": "EN_ATTENTE",
  "totalAmount": 13.80,
  "orderDate": "2026-11-18T19:10:00Z",
  "pickupSlot": "2026-11-20T10:30:00",
  "paymentId": null,
  "items": [
    { "productId": 1, "productName": "Pain de campagne", "merchantId": "merchant-1",
      "quantity": 2, "unitPrice": 3.50, "lineTotal": 7.00 },
    { "productId": 3, "productName": "Fromage de chèvre", "merchantId": "merchant-2",
      "quantity": 1, "unitPrice": 6.80, "lineTotal": 6.80 }
  ]
}
```

Si le même article apparaît plusieurs fois dans la requête, les quantités sont additionnées.

### Panier

```http
POST /api/order/cart/items          {"productId": 1, "quantity": 2}   -> le panier mis à jour
PUT  /api/order/cart/items/1        {"quantity": 3}                   -> le panier mis à jour
DELETE /api/order/cart/items/1                                        -> le panier mis à jour (supprimé s'il devient vide)
GET  /api/order/cart                                                  -> le panier (vide s'il n'existe pas)
POST /api/order/cart/validate       {"pickupSlot": "2026-12-01T10:30:00"}  (corps facultatif)
                                                                      -> la commande EN_ATTENTE
```

### Supprimer une commande (client)

```http
DELETE /api/order/12        -> 204 (la commande passe ANNULEE)
```

### Vue commerçant

```http
GET /api/order/merchant/orders       -> une ligne par commande contenant mes articles
GET /api/order/merchant/orders/12    -> le contenu de la commande, limité à mes articles
```

```json
[
  { "orderId": 12, "orderDate": "2026-11-21T09:15:00Z", "status": "EN_ATTENTE",
    "clientId": "client-1", "itemCount": 3, "totalAmount": 10.60 }
]
```

`itemCount` et `totalAmount` ne comptent que les articles du commerçant connecté. Les commandes annulées restent dans la liste, avec le statut `ANNULEE`.

### Codes d'erreur

Les erreurs suivent le format standard Problem Details (RFC 9457).

| Code | Cas |
|---|---|
| `400` | Corps invalide : `items` vide, `productId` manquant, `quantity` ≤ 0, `pickupSlot` dans le passé ; validation d'un panier vide |
| `401` | En-tête `X-User-Id` ou `X-User-Role` absent |
| `403` | Rôle incorrect ou inconnu (par exemple un commerçant qui passe une commande) |
| `404` | Commande introuvable ou appartenant à un autre client ; article absent du panier |
| `409` | Stock insuffisant ; suppression d'une commande qui n'est plus `EN_ATTENTE` |
| `503` | Service catalogue indisponible |
| `422` | Article inconnu ou inactif |

## Modèle de données (schéma `orders`)

Conforme au MLD commun : base `shoploc-db`, un schéma par service. L'order-service n'écrit que dans le schéma `orders`.

**commande**

| Colonne | Contenu |
|---|---|
| `id_commande` (PK) | identifiant de la commande |
| `date_commande` | date de création |
| `statut` | `PANIER`, `EN_ATTENTE`, `VALIDEE`, `PRETE`, `RETIREE`, `ANNULEE` |
| `montant_total` | total de la commande |
| `creneau_retrait` | créneau de retrait choisi (facultatif) |
| `id_client` | identifiant Keycloak du client (pas de clé étrangère SQL) |
| `nom_client` | nom du client au moment de la validation, figé comme sur un ticket (ajout par rapport au MLD, facultatif) |
| `id_paiement` | → `payment.paiement`, vide tant que la commande n'est pas payée |

**ligne_commande** — clé primaire (`id_commande`, `id_produit`)

| Colonne | Contenu |
|---|---|
| `id_commande` (FK) | → `commande.id_commande` |
| `id_produit` | → `catalog.produit` (pas de clé étrangère SQL) |
| `nom_produit` | copie du nom au moment de la commande |
| `id_commercant` | → `keycloak-db.utilisateur` |
| `quantite` | quantité commandée |
| `prix_unitaire` | prix figé au moment de la commande |

Les références vers les autres services ne sont pas des clés étrangères SQL : la cohérence est assurée par les API (et plus tard par les événements Kafka).

## Dépendances externes

### Catalogue

Le service lit les produits et décrémente le stock en appelant le service catalogue (`HttpProductCatalogClient`), à l'adresse `CATALOG_URL` (par défaut `http://localhost:8081`). Le catalogue doit donc être démarré pour passer une commande.

Endpoints internes du catalogue utilisés (appelés directement, sans passer par la Gateway) :

| Appel | Réponses |
|---|---|
| `GET /api/products/{id}/internal` | `200` `{id, name, price, active, stock, merchantId}` · `404` produit inconnu |
| `POST /api/products/stock/decrement` `{"items":[{"productId","quantity"}]}` | `204` · `409` stock insuffisant · `404` produit inconnu (tout ou rien) |

Si le catalogue ne répond pas, la commande est refusée avec `503`.

### Identité (Keycloak) — simulée pour l'instant

`CurrentUserArgumentResolver` lit les en-têtes `X-User-Id`, `X-User-Role` et `X-User-Name` (facultatif, encodé comme une URL : `H%C3%A9l%C3%A8ne%20Dupr%C3%A9`). Le front les remplit à partir du token Keycloak ; à terme, c'est la Gateway qui les posera après avoir vérifié le token.

À la validation du panier, le nom est enregistré dans la commande (`nom_client`) : le commerçant voit le nom du client au lieu de son identifiant. S'il est absent, la commande est quand même acceptée.

> ⚠️ L'identité par en-têtes sert uniquement au développement : n'importe qui peut envoyer un faux en-tête. Elle doit être remplacée par la vérification du token JWT avant toute mise en production.

## Configuration

La configuration est centralisée dans `shoploc-config-server` (`configurations/shoploc-order-service.yaml`). Ne pas modifier le fichier `application.yaml` local.

| Variable | Valeur par défaut | Rôle |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/shoploc-db` | URL PostgreSQL |
| `DB_USERNAME` | `postgres` | Utilisateur PostgreSQL |
| `DB_PASSWORD` | *(vide)* | Mot de passe PostgreSQL |
| `CATALOG_URL` | `http://localhost:8081` | adresse du service catalogue |
| `SPRING_PROFILES_ACTIVE` | — | `local` pour utiliser `application-local.yaml` (non versionné) |

Le service écoute sur le port `8082`.

## Lancer en local

Prérequis : Java 21 ou plus, Docker (pour les tests), un PostgreSQL local avec la base `shoploc-db`, et le fichier `~/.m2/settings.xml` pour GitHub Packages.

Démarrer les applications dans cet ordre :

1. `shoploc-config-server` (port 8888)
2. `shoploc-catalog-service` (port 8081), avec des produits en base
3. `shoploc-order-service` (port 8082), par exemple avec `SPRING_PROFILES_ACTIVE=local;DB_USERNAME=<ton_utilisateur>`
4. `shoploc-api-gateway` (port 8080)

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
- Client du catalogue (`HttpProductCatalogClientTest`) : appels HTTP vérifiés avec un faux serveur (`MockRestServiceServer`).
- Tests de bout en bout (`OrderFlowIntegrationTest`) : HTTP → controller → service → vrai PostgreSQL, lancé par Testcontainers. Docker doit être démarré. Le catalogue y est remplacé par un faux catalogue en mémoire (`FakeProductCatalogClient`, uniquement dans `src/test`), pour que les tests ne dépendent pas d'un autre service.
- Le build échoue si la couverture de lignes est inférieure à 80 % (JaCoCo, configuré dans `shoploc-parent`).

### Scénario manuel de bout en bout via la Gateway

Avec les produits de test du catalogue (dans l'ordre d'insertion : 1 pain, 2 croissant (stock 5), 3 fromage, 4 confiture inactive ; vérifier les id dans `catalog.produit`).

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
