# ZeroGate - The Reverse SMS Gateway

**Zero SMS Costs. Maximum Verification.**

ZeroGate is a "Reverse SMS Gateway" Android application designed to verify mobile numbers without incurring SMS sending charges on your server. Instead of you sending an OTP to the user (costing you money), the **user sends the OTP to this app** (running on your Android device). ZeroGate then forwards the verification to your server.

## 🚀 Concept: How It Works

1. **User Action**: A user on your website wants to verify their phone number.
2. **Instruction**: Your website shows them a code (e.g., `DON 123456`) and a phone number to send it to (the number of the phone running ZeroGate).
3. **SMS Sent**: The user sends the SMS from their phone.
4. **ZeroGate Intercepts**: This app reads the incoming SMS.
5. **Verification**: If the SMS starts with your configured keyword (e.g., `DON`), the app parses the code.
6. **Webhook**: The app instantly sends a POST request to your server with the sender's phone number and the code.
7. **Success**: Your server validates the code and marks the user as verified.

**Benefit**: You verify the user's possession of the SIM card *and* their willingness to spend a standard SMS charge, filtering out spam/bot users, all while **costing you $0** in SMS gateway fees.

---

## 🛠️ Configuration Guide

Install the app on an Android device with a SIM card.

### 1. App Permissions
On first launch, grant the **SMS Receive/Read** permissions when prompted. The status indicator will turn **GREEN** when ready.

### 2. Settings
Open the configuration screen to set up your integration:

*   **API URL**: The endpoint on your server that will receive the verification webhook.
    *   *Example*: `https://api.yourwebsite.com/v1/verify-sms`
*   **Keyword**: The unique prefix your users must type.
    *   *Default*: `DONIKKAH`
    *   *Advice*: Keep it short and unique (e.g., `VERIFY`, `MYAPP`, `DON`).
*   **Response Payload**: The JSON body sent to your server. You can customize this to match your API's expected format.
    *   *Placeholders*:
        *   `%code%` -> The OTP code parsed from the SMS (e.g., `123456`).
        *   `%phone%` -> The sender's mobile number (e.g., `+15550001234`).
    *   *Default*: `{ "code": "%code%", "phone": "%phone%" }`
*   **Headers**: Add up to 2 custom headers for security or content negotiation.
    *   *Common Use*: `x-api-key: your_secret_key` or `Authorization: Bearer <token>`

---

## 🔌 API Integration

Your server needs an endpoint to accept the POST request from ZeroGate.

### Recommended Security
Since the app communicates over the internet, you should secure your webhook:
1.  **HTTPS**: Always use an `https://` URL.
2.  **API Key**: Set a custom header (e.g., `x-api-key`) in the app and check for it on your server. This prevents unauthorized requests from hitting your endpoint.

### Example Server Implementation (Node.js / Express)

**App Configuration:**
*   **Payload**: `{ "otp": "%code%", "mobile": "%phone%" }`
*   **Header**: `x-webhook-secret: 12345-abcde`

**Server Code:**

```javascript
app.post('/verify-sms', (req, res) => {
    // 1. Security Check
    const secret = req.headers['x-webhook-secret'];
    if (secret !== '12345-abcde') {
        return res.status(401).json({ error: 'Unauthorized' });
    }

    // 2. Parse Data
    const { otp, mobile } = req.body;

    // 3. Verify Logic
    console.log(`Received verification for ${mobile} with code ${otp}`);
    
    // ... Find user by 'mobile' or 'otp' in your DB and mark verified ...

    return res.status(200).json({ success: true });
});
```

### Parsing Logic
ZeroGate scans incoming SMS messages for the pattern: 
`[KEYWORD] [CODE]` (Case insensitive).

*   **Keyword**: `DON`
*   **User Sends**: `don 998877` -> **Parsed Code**: `998877`
*   **User Sends**: `Don 998877` -> **Parsed Code**: `998877`
*   **User Sends**: `Hello don 998877` -> **Ignored** (Must start with keyword).

---

## ❓ FAQ

**Q: Do I need internet on the phone?**
A: Yes, the phone running ZeroGate requires an active internet connection (WiFi or Data) to call your API.

**Q: What if the API call fails?**
A: The app logs the failure. Currently, it does not automatically retry indefinitely, but the internal worker will attempt to resend if it's a transient network error.

**Q: Is "ZeroGate" the final name?**
A: This is the suggested branding for the open-source version. You can recompile the app with any name you wish.
