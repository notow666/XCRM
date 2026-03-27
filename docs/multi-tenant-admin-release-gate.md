# Multi-tenant Admin Center Release Gate

## 1. Functional Gates

- [ ] Dynamic tenant provision succeeds end-to-end (create DB, migrate, register datasource).
- [ ] Repeated provision request with same `tenantId` is idempotent and returns existing tenant info.
- [ ] Provision failure triggers rollback (master metadata + datasource unregister + optional drop database).
- [ ] Platform admin can list tenants, view details, enable/disable tenant, trigger rerun migration.
- [ ] Platform admin can query platform audit logs.
- [ ] Non-admin user is blocked from `/platform/admin/**` APIs.

## 2. Isolation Gates

- [ ] Missing/invalid `tenantId` returns illegal request.
- [ ] Session tenant and `X-Tenant-ID` mismatch is forbidden.
- [ ] A/B tenant users cannot cross-tenant access data.
- [ ] Logout clears frontend `tenantId/orgId` and redirects to previous `/:tenantId/login`.

## 3. Frontend Quality Gates

- [ ] `pnpm -C frontend/packages/web run type:check` passes.
- [ ] `pnpm -C frontend/packages/mobile run type:check` passes.
- [ ] Web and mobile eslint checks pass for changed files.
- [ ] `management-center` menu is visible only for platform admin.

## 4. Observability Gates

- [ ] Audit records written for provision/enable/disable/rerun-migrate operations.
- [ ] Tenant health check exposes metadata, datasource registration, jdbc reachability, migration version.
- [ ] Operation errors include tenantId and action context in logs.

## 5. Gray Release Plan

1. Deploy to test environment with isolated `crm_master`.
2. Batch provision 10-50 tenants via management center.
3. Verify each tenant can login and load core homepage.
4. Run tenant disable/enable and rerun migration smoke test.
5. Compare audit logs with executed operations.
6. Roll out production in low-traffic window and watch error/latency dashboards for 24h.
