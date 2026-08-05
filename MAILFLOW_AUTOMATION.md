# Mailflow-Automation Implementation

## 🎯 Überblick

Die Mailflow-Automation ermöglicht automatisierte, konfigurierbare E-Mail-Sequenzen für Kunden:

- **Beispiel:** Customer Onboarding: Welcome-Mail → Follow-up nach 3 Tagen → Abschluss nach 7 Tagen
- **Deployment:** Automatische Verarbeitung nachts um 02:00 Uhr via Spring Scheduler
- **Retry-Logik:** Bis zu 3 Versuche pro Step bei Fehlern
- **Opt-out:** Automatische Übersprung-Logik wenn Einwilligung widerrufen

---

## 📋 Implementierungsstatus

### ✅ Vollständig (12/12 Tasks)

| Task | Komponente | Status |
|------|-----------|--------|
| 1-9 | Backend (Entities, Services, Scheduler, API, Tests) | ✅ |
| 10-12 | Frontend (Customers Page, Mailflow UI) | ✅ |

### Backend-Komponenten

```
src/main/java/de/toengi/cili/
├── model/entity/
│   ├── MailflowInstance.java      (FK: customerId, flowName, startedAt)
│   └── MailflowStepStatus.java    (FK: instanceId, stepId, scheduledFor, status)
├── model/enums/
│   └── MailflowStepState.java     (PENDING, SENT, SKIPPED, ERROR, FAILED)
├── config/
│   ├── MailflowConfig.java        (YAML @ConfigurationProperties)
│   ├── MailflowConfigValidator.java (Fail-fast validation)
│   └── MailflowBatchScheduler.java (@Scheduled cron="${cili.mailflows.batch-cron:-}")
├── repository/
│   ├── MailflowInstanceRepository.java
│   └── MailflowStepStatusRepository.java (findDueSteps Query)
├── service/
│   ├── MailflowService.java       (startFlow, listInstances, listAvailableFlows)
│   ├── MailflowStepProcessor.java (processDueSteps, per-step TransactionTemplate)
│   └── MailService.java           (sendSync() refactored)
├── controller/
│   └── MailflowController.java    (GET/POST /api/mailflows, /api/customers/{id}/mailflows)
└── dto/mailflow/
    ├── MailflowInstanceDto.java
    ├── MailflowStepDto.java
    ├── StartMailflowRequest.java
    └── AvailableMailflowDto.java
```

### Frontend-Komponenten

```
frontend/src/
├── api/
│   ├── customers.ts       (getCustomers, getCustomer, createCustomer)
│   └── mailflows.ts       (getAvailableMailflows, getCustomerMailflows, startMailflow)
├── hooks/
│   ├── useCustomers.ts    (useCustomers, useCustomer, useCreateCustomer)
│   └── useMailflows.ts    (useAvailableMailflows, useCustomerMailflows, useStartMailflow)
├── pages/
│   ├── CustomersPage.tsx     (Kundenliste + Neuer Kunde Dialog)
│   └── CustomerDetailPage.tsx (Kundendetails + Mailflow-Sektion)
├── components/customer/
│   └── MailflowSection.tsx (Flow-Auswahl + Step-Tabelle)
└── types/api.ts (neue Typen: CustomerDto, MailflowInstanceDto, etc.)
```

### Datenbankschema

```sql
CREATE TABLE mailflow_instances (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  flow_name VARCHAR(100) NOT NULL,
  started_at DATETIME NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  KEY idx_mailflow_instances_customer_flow (customer_id, flow_name),
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE TABLE mailflow_step_status (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT NOT NULL,
  step_id VARCHAR(100) NOT NULL,
  scheduled_for DATE NOT NULL,
  sent_at DATETIME,
  status VARCHAR(20) DEFAULT 'PENDING',
  attempt_count INT DEFAULT 0,
  last_error VARCHAR(1000),
  UNIQUE KEY (instance_id, step_id),
  KEY idx_mailflow_step_status_due (status, scheduled_for),
  FOREIGN KEY (instance_id) REFERENCES mailflow_instances(id) ON DELETE CASCADE
);
```

---

## 🚀 Verwendung

### 1. Konfiguration

Fügen Sie folgende Sektion in `application-prod.yml` ein:

```yaml
cili:
  mailflows:
    batch-cron: "0 0 2 * * *"  # 02:00 Uhr täglich
    flows:
      welcome:
        description: "Willkommens-Sequenz"
        steps:
          - id: welcome
            delay-days: 0
            template: customer-welcome
            subject: "Willkommen!"
          - id: followup
            delay-days: 3
            template: customer-followup
            subject: "Wie geht's?"
```

Siehe `MAILFLOW_EXAMPLE_CONFIG.yml` für vollständiges Beispiel.

### 2. Templates erstellen

Thymeleaf-Templates unter `src/main/resources/templates/mail/`:

```html
<!-- customer-welcome.html -->
<h2>Willkommen, <span th:text="${customer.name}">Kunde</span>!</h2>
<p>E-Mail: <span th:text="${customer.email}">email@example.com</span></p>
<p th:if="${sponsor}">Betreuer: <span th:text="${sponsor.email}">sponsor@example.com</span></p>
<a th:href="${unsubscribeUrl}">Abmelden</a>
```

**Verfügbare Variablen im Template:**
- `${customer}` — Customer Entity (id, name, email, consentGranted, ...)
- `${sponsor}` — User Entity (optional, kann null sein)
- `${unsubscribeUrl}` — Abmelde-Link

### 3. Kunden erstellen

1. Öffnen Sie http://localhost:8080/
2. Navigieren Sie zu "Kunden" (neue Seite in der Sidebar)
3. Klicken Sie "Neuer Kunde"
4. Füllen Sie Name und E-Mail aus

### 4. Flow starten

1. Öffnen Sie die Kundendetail-Seite
2. In der "Mailflows"-Sektion: Wählen Sie einen Flow aus
3. Klicken Sie "Starten"

### 5. Automatische Verarbeitung

Der Batch-Scheduler läuft automatisch:
- **Zeitpunkt:** 02:00 Uhr täglich (konfigurierbar)
- **Verarbeitung:** Alle Steps mit `scheduledFor <= heute` werden verarbeitet
- **Consent-Check:** Wenn `customer.consentGranted == false` → Status wird auf SKIPPED
- **Retry:** Bei Fehler bis zu 3 Versuche, dann FAILED

---

## 🔧 REST-API

### Endpoints

**GET /api/mailflows**
```json
Response: [
  {
    "flowName": "welcome",
    "description": "Willkommens-Sequenz"
  }
]
```

**POST /api/customers/{customerId}/mailflows**
```json
Request: { "flowName": "welcome" }
Response: {
  "id": 1,
  "flowName": "welcome",
  "description": "Willkommens-Sequenz",
  "startedAt": "2026-08-05T14:00:00",
  "status": "RUNNING",
  "steps": [
    {
      "stepId": "welcome",
      "scheduledFor": "2026-08-05",
      "sentAt": "2026-08-05T14:02:30",
      "status": "SENT",
      "attemptCount": 0,
      "lastError": null
    }
  ]
}
```

**GET /api/customers/{customerId}/mailflows**
```json
Response: [
  { /* MailflowInstanceDto */ }
]
```

---

## 🧪 Testing

Alle 5 Integration-Tests bestanden:

```bash
mvn test -Dtest=MailflowIntegrationTest
# Tests run: 5, Failures: 0, Errors: 0 ✓
```

---

## 🔑 Key Features

✅ **YAML-Konfiguration** — Flows ohne Code-Änderung konfigurierbar  
✅ **Fail-Fast Validierung** — Templates & Cron-Ausdruck werden beim Start validiert  
✅ **Per-Step Transaktionen** — TransactionTemplate verhindert Self-Invocation Problem  
✅ **Retry-Logik** — Bis zu 3 Versuche mit error tracking  
✅ **Consent Management** — Automatische Übersprung-Logik  
✅ **Synchrone Mail-API** — sendSync() für strukturiertes Error-Handling  
✅ **Nacht-Batch** — Automatische Verarbeitung via @Scheduled  
✅ **Full Integration** — REST-API + React Frontend + Unit/Integration Tests  

---

## 📦 Commits

```
ab66eb7 feat(mailflow): add frontend for Customers and Mailflows management
9fb80c3 fix(mailflow): correct AutoConfigureMockMvc import and remove empty flows map
b2ab59f feat(mailflow): add MailflowService, Processor, Scheduler, Controller and Integration tests
e5ed522 feat(mailflow): add Mailflow DTOs
52411db refactor(mail): extract synchronous sendSync() from MailService.send()
87e1181 feat(mailflow): add MailflowConfig YAML binding with fail-fast validation
3c9401e feat(mailflow): add MailflowInstance/MailflowStepStatus entities and schema
```

---

## 🎓 Beispiel: Customer Welcome Flow

Siehe `MAILFLOW_EXAMPLE_CONFIG.yml` für eine vollständige Konfiguration mit:
- Welcome-Email (Tag 0)
- Follow-up (Tag 3)
- Abschluss (Tag 7)
