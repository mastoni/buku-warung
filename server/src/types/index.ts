export type LicenseStatus = 'PENDING' | 'ACTIVE' | 'REVOKED';
export type DeviceStatus = 'ACTIVE' | 'REVOKED';
export type RecoveryStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type ValidationResultStatus =
  | 'VALID'
  | 'INVALID'
  | 'DEVICE_MISMATCH'
  | 'EMAIL_MISMATCH'
  | 'REVOKED';

export interface LicenseRecord {
  id: number;
  license_uuid: string;
  license_code_hash: string;
  owner_email_canonical: string;
  owner_email_hash: string;
  product: string;
  price: number;
  status: LicenseStatus;
  created_at: number;
  activated_at: number | null;
  revoked_at: number | null;
  updated_at: number;
}

export interface LicenseDeviceRecord {
  id: number;
  license_id: number;
  device_binding: string;
  status: DeviceStatus;
  first_activated_at: number;
  last_validated_at: number;
  revoked_at: number | null;
  created_at: number;
  updated_at: number;
}

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'LICENSE_CREATED'
  | 'DELIVERED'
  | 'ACTIVE'
  | 'COMPLETED';

export type PaymentStatus = 'UNPAID' | 'PAID' | 'REJECTED';

export interface OrderRecord {
  id: number;
  order_number: string;
  customer_name: string;
  customer_contact: string;
  owner_email: string;
  product: string;
  amount: number;
  status: OrderStatus;
  payment_status: PaymentStatus;
  license_id: number | null;
  payment_method: string | null;
  payment_reference: string | null;
  verified_at: number | null;
  verified_by: string | null;
  delivered_at: number | null;
  delivered_by: string | null;
  notes: string | null;
  created_at: number;
  updated_at: number;
  lead_token: string | null;
  utm_source: string | null;
  utm_medium: string | null;
  utm_campaign: string | null;
  utm_content: string | null;
  encrypted_delivery_license_code?: string | null;
}

export interface AuditLogRecord {
  id: number;
  action: string;
  license_id: number | null;
  old_state: string | null;
  new_state: string | null;
  actor: string;
  reason: string | null;
  created_at: number;
}

export interface RecoveryRequestRecord {
  id: number;
  license_id: number;
  old_device_binding: string | null;
  new_device_binding: string;
  status: RecoveryStatus;
  reason: string | null;
  created_at: number;
  resolved_at: number | null;
}

export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
}

export interface ActivateRequest {
  licenseCode: string;
  ownerEmail: string;
  deviceBinding: string;
}

export interface ValidateRequest {
  licenseCode: string;
  ownerEmail: string;
  deviceBinding: string;
}

export interface RecoverRequest {
  licenseCode: string;
  ownerEmail: string;
  newDeviceBinding: string;
  reason?: string;
}

export interface AdminCreateLicenseRequest {
  ownerEmail: string;
  product?: string;
  price?: number;
  customerName?: string;
  customerContact?: string;
}

export interface AdminRebindRequest {
  licenseId: number;
  newDeviceBinding: string;
  reason?: string;
}

export interface CreateOrderRequest {
  customerName: string;
  customerContact: string;
  ownerEmail: string;
  product?: string;
  amount?: number;
  notes?: string;
  leadToken?: string;
  utm_source?: string;
  utm_medium?: string;
  utm_campaign?: string;
  utm_content?: string;
}

export interface LandingTrackRequest {
  eventType: string;
  leadToken?: string;
  utm_source?: string;
  utm_medium?: string;
  utm_campaign?: string;
  utm_content?: string;
}

export interface LandingTrackResponse {
  success: boolean;
  data: {
    leadToken: string;
    action: 'created' | 'matched' | 'updated';
  };
}

export interface FunnelEventRecord {
  id: number;
  lead_token: string;
  event_type: string;
  event_data: string | null;
  ip_hash: string | null;
  user_agent: string | null;
  created_at: number;
}

export interface VerifyPaymentRequest {
  paymentMethod?: string;
  paymentReference?: string;
  notes?: string;
}

export interface MarkDeliveredRequest {
  notes?: string;
}

export interface OrderSummaryMetrics {
  totalOrders: number;
  pendingPayment: number;
  paid: number;
  pendingLicense: number;
  delivered: number;
  active: number;
  paidRevenue: number;
  actionRequiredCount: number;
  attributedOrders: number;
  unattributedOrders: number;
}

export interface FunnelStage {
  name: string;
  count: number;
}

export interface FunnelConversion {
  from: string;
  to: string;
  rate: number;
  numerator: number;
  denominator: number;
}

export interface AttributionEntry {
  dimension: string;
  value: string;
  leads: number;
  orders: number;
  paidOrders: number;
  licenses: number;
  activatedCustomers: number;
}

export interface FunnelAnalytics {
  dateRange: { start: number; end: number };
  funnel: FunnelStage[];
  conversions: FunnelConversion[];
  attribution: {
    sources: AttributionEntry[];
    mediums: AttributionEntry[];
    campaigns: AttributionEntry[];
    contents: AttributionEntry[];
  };
  northStar: {
    activatedPaidCustomers: number;
  };
}

export interface PromotionRecord {
  id: number;
  product: string;
  name: string;
  enabled: number; // 0 or 1
  normal_price: number;
  promo_price: number;
  starts_at: number;
  expires_at: number;
  timezone: string;
  show_countdown: number; // 0 or 1
  created_at: number;
  updated_at: number;
}

export interface PricingResult {
  product: string;
  isPromoActive: boolean;
  effectivePrice: number;
  normalPrice: number;
  promoPrice: number;
  promoName: string;
  startsAt: number;
  expiresAt: number;
  timezone: string;
  showCountdown: boolean;
  serverTime: number;
}

export interface UpdatePromotionRequest {
  name?: string;
  enabled?: boolean | number;
  normalPrice?: number;
  promoPrice?: number;
  startsAt?: number | string;
  expiresAt?: number | string;
  timezone?: string;
  showCountdown?: boolean | number;
}

