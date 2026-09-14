import { config } from '../config/index.js';

export interface CreateLicensePayload {
  ownerEmail: string;
  product?: string;
  price?: number;
  customerName?: string;
  customerContact?: string;
}

export interface RebindPayload {
  licenseId: number;
  newDeviceBinding: string;
  reason?: string;
}

export interface CreateOrderPayload {
  customerName: string;
  customerContact: string;
  ownerEmail: string;
  product?: string;
  amount?: number;
  notes?: string;
}

export interface VerifyPaymentPayload {
  paymentMethod?: string;
  paymentReference?: string;
  notes?: string;
}

export interface MarkDeliveredPayload {
  notes?: string;
}

export class LicenseClient {
  private customUrl?: string;
  private customApiKey?: string;

  constructor(customUrl?: string, customApiKey?: string) {
    this.customUrl = customUrl;
    this.customApiKey = customApiKey;
  }

  private get baseUrl(): string {
    return this.customUrl || config.licenseServerUrl;
  }

  private get apiKey(): string {
    return this.customApiKey || config.adminApiKey;
  }

  private async request<T = any>(path: string, options: RequestInit = {}): Promise<{ status: number; data: T }> {
    const url = `${this.baseUrl}${path}`;
    const headers: Record<string, string> = {
      'Authorization': `Bearer ${this.apiKey}`,
      ...(options.headers as Record<string, string> || {})
    };

    if (options.body) {
      headers['Content-Type'] = 'application/json';
    }

    try {
      const response = await fetch(url, {
        ...options,
        headers
      });

      const json = await response.json().catch(() => ({}));
      return {
        status: response.status,
        data: json as T
      };
    } catch (error: any) {
      return {
        status: 503,
        data: {
          success: false,
          error: {
            code: 'LICENSE_SERVER_UNAVAILABLE',
            message: `Could not connect to License Server at ${this.baseUrl}: ${error.message}`
          }
        } as any
      };
    }
  }

  async createLicense(payload: CreateLicensePayload) {
    return this.request('/v1/admin/licenses', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  }

  async listLicenses() {
    return this.request('/v1/admin/licenses', {
      method: 'GET'
    });
  }

  async getLicenseDetail(id: number) {
    return this.request(`/v1/admin/licenses/${id}`, {
      method: 'GET'
    });
  }

  async revokeLicense(id: number, reason?: string) {
    return this.request(`/v1/admin/licenses/${id}/revoke`, {
      method: 'POST',
      body: JSON.stringify({ reason })
    });
  }

  async revokeDevice(deviceId: number, reason?: string) {
    return this.request(`/v1/admin/devices/${deviceId}/revoke`, {
      method: 'POST',
      body: JSON.stringify({ reason })
    });
  }

  async rebindDevice(payload: RebindPayload) {
    return this.request('/v1/admin/license/rebind', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  }

  async getAuditLogs(licenseId?: number) {
    const query = licenseId ? `?licenseId=${licenseId}` : '';
    return this.request(`/v1/admin/audit-logs${query}`, {
      method: 'GET'
    });
  }

  async getHealth() {
    return this.request('/health', {
      method: 'GET'
    });
  }

  /* Sales & Order methods (C.10.1) */

  async createOrder(payload: CreateOrderPayload) {
    return this.request('/v1/admin/orders', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  }

  async listOrders(filter?: string, search?: string) {
    const params = new URLSearchParams();
    if (filter) params.append('filter', filter);
    if (search) params.append('search', search);
    const query = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/v1/admin/orders${query}`, {
      method: 'GET'
    });
  }

  async getOrderDetail(id: number) {
    return this.request(`/v1/admin/orders/${id}`, {
      method: 'GET'
    });
  }

  async verifyOrderPayment(id: number, payload: VerifyPaymentPayload) {
    return this.request(`/v1/admin/orders/${id}/verify-payment`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  }

  async generateLicenseForOrder(id: number) {
    return this.request(`/v1/admin/orders/${id}/generate-license`, {
      method: 'POST',
      body: JSON.stringify({})
    });
  }

  async markOrderDelivered(id: number, payload: MarkDeliveredPayload = {}) {
    return this.request(`/v1/admin/orders/${id}/mark-delivered`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  }
}
