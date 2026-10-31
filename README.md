# Shoploc Order Service

## Overview

`shoploc-order-service` is a backend microservice responsible for managing
the order lifecycle of the Shoploc platform.

It handles shopping carts, orders, order items, and the different states
of an order from creation to completion or cancellation.

The service is part of the Shoploc microservices architecture.

## Responsibilities

The Order Service is responsible for:

- Managing shopping carts
- Creating orders
- Managing order items
- Managing the order lifecycle
- Updating order status
- Cancelling orders
- Calculating order totals
- Managing the relationship between orders and products
- Providing order APIs to clients and other Shoploc services
- Orchestrating interactions with other services when required

The Order Service contains order-related business logic.

Product and inventory management remain the responsibility of
`shoploc-catalog-service`.

Payment processing remains the responsibility of
`shoploc-payment-service`.

Authentication and identity management remain the responsibility of
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