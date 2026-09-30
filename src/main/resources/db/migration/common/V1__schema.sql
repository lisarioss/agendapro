CREATE TABLE tenants (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, slug VARCHAR(60) NOT NULL UNIQUE,
 timezone VARCHAR(80) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE professionals (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), name VARCHAR(120) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(tenant_id,id)
);
CREATE TABLE users (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), name VARCHAR(120) NOT NULL,
 email VARCHAR(254) NOT NULL, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL CHECK(role IN ('ADMIN','ATTENDANT','PROFESSIONAL')),
 professional_id UUID, active BOOLEAN NOT NULL DEFAULT TRUE, token_version INTEGER NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,email), UNIQUE(tenant_id,id),
 FOREIGN KEY(tenant_id,professional_id) REFERENCES professionals(tenant_id,id),
 CHECK ((role = 'PROFESSIONAL' AND professional_id IS NOT NULL) OR (role <> 'PROFESSIONAL' AND professional_id IS NULL))
);
CREATE TABLE clients (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), name VARCHAR(120) NOT NULL,
 email VARCHAR(254), phone VARCHAR(30), active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(tenant_id,id)
);
CREATE TABLE services (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), name VARCHAR(120) NOT NULL,
 duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 5 AND 480),
 price NUMERIC(12,2) NOT NULL CHECK(price >= 0), active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(tenant_id,id)
);
CREATE TABLE professional_services (
 tenant_id UUID NOT NULL, professional_id UUID NOT NULL, service_id UUID NOT NULL,
 PRIMARY KEY(tenant_id,professional_id,service_id),
 FOREIGN KEY(tenant_id,professional_id) REFERENCES professionals(tenant_id,id),
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id)
);
CREATE TABLE working_hours (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, professional_id UUID NOT NULL,
 day_of_week INTEGER NOT NULL CHECK(day_of_week BETWEEN 1 AND 7),
 start_time TIME NOT NULL, end_time TIME NOT NULL, CHECK(end_time > start_time),
 FOREIGN KEY(tenant_id,professional_id) REFERENCES professionals(tenant_id,id)
);
CREATE TABLE time_off (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, professional_id UUID NOT NULL,
 starts_at TIMESTAMP WITH TIME ZONE NOT NULL, ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
 reason VARCHAR(200) NOT NULL, CHECK(ends_at > starts_at),
 FOREIGN KEY(tenant_id,professional_id) REFERENCES professionals(tenant_id,id)
);
CREATE TABLE appointments (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), professional_id UUID NOT NULL,
 client_id UUID NOT NULL, service_id UUID NOT NULL,
 starts_at TIMESTAMP WITH TIME ZONE NOT NULL, ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','CONFIRMED','COMPLETED','CANCELED','NO_SHOW')),
 price NUMERIC(12,2) NOT NULL CHECK(price >= 0), notes VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(ends_at > starts_at), UNIQUE(tenant_id,id),
 FOREIGN KEY(tenant_id,professional_id) REFERENCES professionals(tenant_id,id),
 FOREIGN KEY(tenant_id,client_id) REFERENCES clients(tenant_id,id),
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id)
);
CREATE TABLE appointment_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, appointment_id UUID NOT NULL,
 actor_id UUID NOT NULL, action VARCHAR(40) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(tenant_id,appointment_id) REFERENCES appointments(tenant_id,id),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES users(tenant_id,id)
);
CREATE INDEX idx_clients_tenant ON clients(tenant_id);
CREATE INDEX idx_services_tenant ON services(tenant_id);
CREATE INDEX idx_professionals_tenant ON professionals(tenant_id);
CREATE INDEX idx_appointments_calendar ON appointments(tenant_id,professional_id,starts_at,ends_at);
CREATE INDEX idx_time_off_calendar ON time_off(tenant_id,professional_id,starts_at,ends_at);
CREATE INDEX idx_hours ON working_hours(tenant_id,professional_id,day_of_week);
