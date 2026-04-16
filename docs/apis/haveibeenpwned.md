# HaveIBeenPwned API Integration

## Overview
HaveIBeenPwned provides data breach exposure information to check if email addresses or passwords have been compromised.

## API Endpoints

### Breached Account
```
GET https://haveibeenpwned.com/api/v3/breachedaccount/{account}
```

Parameters:
- `account`: Email address to check
- `truncateResponse`: Truncate response (true/false)
- `domain`: Filter by domain
- `includeUnverified`: Include unverified breaches (true/false)
- `apiKey`: Your API key (required for non-browser requests)

### All Breaches
```
GET https://haveibeenpwned.com/api/v3/breaches
```

Parameters:
- `domain`: Filter by domain

### Single Breach
```
GET https://haveibeenpwned.com/api/v3/breach/{name}
```

Parameters:
- `name`: Name of the breach

### Data Classes
```
GET https://haveibeenpwned.com/api/v3/dataclasses
```

### Paste Account
```
GET https://haveibeenpwned.com/api/v3/pasteaccount/{account}
```

Parameters:
- `account`: Email address to check
- `apiKey`: Your API key (required for non-browser requests)

### Password Range
```
GET https://api.pwnedpasswords.com/range/{hashPrefix}
```

Parameters:
- `hashPrefix`: First 5 characters of SHA-1 hash

## Authentication
For breached account and paste account endpoints, include your API key in the header:
```
hibp-api-key: YOUR_API_KEY
```

For password range endpoint, no authentication is required.

## Response Format
Breached Account Response:
```json
[
  {
    "Name": "Adobe",
    "Title": "Adobe",
    "Domain": "adobe.com",
    "BreachDate": "2013-08-01",
    "AddedDate": "2013-12-04T00:00:00Z",
    "ModifiedDate": "2013-12-04T00:00:00Z",
    "PwnCount": 152449644,
    "Description": "In October 2013, 153 million Adobe accounts were breached...",
    "LogoPath": "https://haveibeenpwned.com/Content/Images/PwnedLogos/Adobe.png",
    "DataClasses": [
      "Email addresses",
      "Password hints",
      "Passwords",
      "Usernames"
    ],
    "IsVerified": true,
    "IsFabricated": false,
    "IsSensitive": false,
    "IsRetired": false,
    "IsSpamList": false,
    "IsMalware": false
  }
]
```

Password Range Response:
```
0018A45C4D1DEF81644B54AB7F969B88D65:1
00D4F6E8FA6EECAD2A3AA415ECAA74229FC:2
011053FD01B7953215CFE9CE6730B5198C6:1
```

## Rate Limits
- Free usage: Rate limited to 1 request per 1.5 seconds
- Commercial API keys: Higher rate limits available
- Enterprise: Custom rate limits

## Error Responses
- 400: Bad Request - Invalid email address
- 401: Unauthorized - Missing or invalid API key
- 403: Forbidden - No user agent specified
- 404: Not Found - Email address not found in breaches
- 429: Too Many Requests - Rate limit exceeded
- 503: Service Unavailable - API temporarily unavailable

## Implementation Notes
- Implement proper rate limiting handling with delays
- Use truncated responses when full details aren't needed
- Implement exponential backoff for rate limiting
- Hash passwords with SHA-1 before sending to API (only first 5 chars sent)
- Handle different breach severity levels appropriately
- Cache breach information to reduce API usage
- Validate email format before sending to API
- Handle unverified breaches with appropriate warnings
- Consider batching requests for multiple email addresses
- Implement proper error handling for different response codes
- Respect user privacy and only check emails with permission