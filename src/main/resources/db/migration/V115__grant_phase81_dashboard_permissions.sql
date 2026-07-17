-- Grant the dashboard permissions introduced by the Phase 81 dashboard controllers.
INSERT INTO smos_permissions(resource, action, description) VALUES
    ('dashboard', 'participant', 'View participant health dashboard'),
    ('dashboard', 'transaction', 'View transaction summary dashboard'),
    ('dashboard', 'infrastructure', 'View infrastructure health dashboard'),
    ('dashboard', 'dr', 'View disaster recovery dashboard')
ON CONFLICT (resource, action) DO NOTHING;

-- System and operations administrators retain their established broad dashboard access.
INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM smos_roles r
JOIN smos_permissions p ON p.resource = 'dashboard'
WHERE r.name IN ('SYSTEM_ADMIN', 'OPS_ADMIN', 'AUDITOR', 'READ_ONLY')
  AND p.action IN ('participant', 'transaction', 'infrastructure', 'dr')
ON CONFLICT DO NOTHING;
