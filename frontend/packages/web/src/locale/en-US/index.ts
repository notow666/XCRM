import common from './common';
import localeSettings from './settings';
import sys from './sys';
import dayjsLocale from 'dayjs/locale/en';

const _Cmodules: any = import.meta.glob('../../components/**/locale/en-US.ts', { eager: true });
const _Vmodules: any = import.meta.glob('../../views/**/locale/en-US.ts', { eager: true });
let result = {};
Object.keys(_Cmodules).forEach((key) => {
  const defaultModule = _Cmodules[key as any].default;
  if (!defaultModule) return;
  result = { ...result, ...defaultModule };
});
Object.keys(_Vmodules).forEach((key) => {
  const defaultModule = _Vmodules[key as any].default;
  if (!defaultModule) return;
  result = { ...result, ...defaultModule };
});

export default {
  message: {
    'menu.workbench': 'Home',
    'menu.settings': 'Settings',
    'menu.collapsedSettings': 'System',
    'menu.settings.org': 'Organization',
    'menu.settings.mmbaDevice': 'Device management',
    'menu.settings.permission': 'Roles',
    'menu.settings.moduleSetting': 'Module',
    'menu.opportunity': 'Opportunity',
    'menu.quotation': 'Quotation',
    'menu.collapsedOpportunity': 'Opportunity',
    'menu.collapsedProduct': 'Product',
    'menu.clue': 'Lead',
    'menu.customer': 'Account',
    'menu.contact': 'Contact',
    'menu.dashboard': 'Dashboard',
    'menu.agent': 'Agent',
    'menu.tender': 'Tender',
    'menu.managementCenter': 'Management Center',
    'menu.managementCenter.overview': 'Overview',
    'menu.managementCenter.tenant': 'Tenants',
    'menu.managementCenter.audit': 'Audit',
    'menu.managementCenter.dataSpecialist': 'Data specialists',
    'managementCenter.dataSpecialist.keywordPlaceholder': 'Username',
    'managementCenter.dataSpecialist.createTitle': 'New data specialist',
    'managementCenter.dataSpecialist.editTitle': 'Edit data specialist',
    'managementCenter.dataSpecialist.username': 'Username',
    'managementCenter.dataSpecialist.initialPassword': 'Initial password',
    'managementCenter.dataSpecialist.passwordOptional': 'New password (leave blank to keep)',
    'managementCenter.dataSpecialist.enabled': 'Enabled',
    'managementCenter.dataSpecialist.tenantIds': 'Importable tenants',
    'managementCenter.dataSpecialist.tenantMultiHint':
      'One-to-many: select multiple tenants this specialist may import into.',
    'managementCenter.dataSpecialist.tenantFilterPlaceholder': 'Filter by tenant name or ID',
    'managementCenter.dataSpecialist.selectedTenantCount': '{count} tenant(s) selected',
    'managementCenter.dataSpecialist.tenantNotInList': 'Not in current list; binding kept',
    'managementCenter.dataSpecialist.tenantFilterEmpty': 'No matching tenants',
    'managementCenter.dataSpecialist.clearTenantSelection': 'Clear selection',
    'managementCenter.dataSpecialist.usernameRequired': 'Username is required',
    'managementCenter.dataSpecialist.passwordRequired': 'Password is required',
    'managementCenter.dataSpecialist.tenantRequired': 'Select at least one tenant',
    'managementCenter.dataSpecialist.createSuccess': 'Created',
    'managementCenter.dataSpecialist.updateSuccess': 'Saved',
    'managementCenter.dataSpecialist.enabledOn': 'Enabled',
    'managementCenter.dataSpecialist.enabledOff': 'Disabled',
    'managementCenter.dataSpecialist.createTime': 'Created at',
    'managementCenter.dataSpecialist.id': 'Specialist ID',
    'managementCenter.dataSpecialist.loadTenantsFailed': 'Failed to load tenants. Please try again.',
    'menu.settings.businessSetting': 'Enterprise',
    'menu.settings.license': 'License',
    'menu.settings.messageSetting': 'Notification',
    'menu.settings.log': 'Logs',
    'menu.report': 'Reports',
    'menu.reportCollapsed': 'Reports',
    'menu.report.employee': 'Employee analytics',
    'menu.report.contract': 'Contract analytics',
    'menu.report.employeeFollowUp': 'Employee follow-up analysis',
    'menu.mmbaAudit': 'Audit',
    'menu.mmbaAuditCollapsed': 'Audit',
    'navbar.action.locale': 'Switch to English',
    ...sys,
    ...localeSettings,
    ...result,
    ...common,
  },
  dayjsLocale,
  dayjsLocaleName: 'en-US',
};
