# Hunter.io API Integration

## Overview
Hunter.io specializes in email discovery and verification services for professionals and businesses.

## API Endpoints

### Domain Search
```
GET https://api.hunter.io/v2/domain-search
```

Parameters:
- `domain`: Domain name to search
- `company`: Company name (alternative to domain)
- `type`: Type of emails to return ("personal" or "generic")
- `seniority`: Seniority level ("junior", "senior", "executive")
- `department`: Department ("executive", "it", "finance", etc.)
- `limit`: Number of emails to return (max 100)
- `offset`: Offset for pagination

### Email Finder
```
GET https://api.hunter.io/v2/email-finder
```

Parameters:
- `domain`: Domain name
- `first_name`: First name
- `last_name`: Last name
- `full_name`: Full name (alternative to first_name and last_name)
- `company`: Company name (alternative to domain)

### Email Verifier
```
GET https://api.hunter.io/v2/email-verifier
```

Parameters:
- `email`: Email address to verify

### Lead Lists
```
GET https://api.hunter.io/v2/leads/lists
```

Parameters:
- `limit`: Number of lists to return
- `offset`: Offset for pagination

### Lead List Creation
```
POST https://api.hunter.io/v2/leads/lists
```

Body Parameters:
- `name`: Name of the lead list
- `domain`: Domain associated with the list

## Authentication
Include your API key as a parameter:
```
api_key=YOUR_API_KEY
```

## Response Format
Responses are in JSON format with the following structure:
```json
{
  "data": {
    "domain": "example.com",
    "disposable": false,
    "webmail": false,
    "accept_all": false,
    "pattern": "{first}.{last}@example.com",
    "organization": "Example Inc.",
    "country": "US",
    "emails": [
      {
        "value": "john.doe@example.com",
        "type": "personal",
        "confidence": 99,
        "sources": [
          {
            "domain": "example.com",
            "uri": "https://example.com/about",
            "extracted_on": "2023-01-01",
            "last_seen_on": "2023-12-01",
            "still_on_page": true
          }
        ],
        "first_name": "John",
        "last_name": "Doe",
        "position": "CEO",
        "seniority": "executive",
        "department": "management",
        "verified": true,
        "unreachable_direct": false
      }
    ],
    "linked_domains": [],
    "success": true
  },
  "meta": {
    "results": 10,
    "limit": 10,
    "offset": 0,
    "search": "example.com"
  }
}
```

Email Verifier Response:
```json
{
  "data": {
    "email": "john.doe@example.com",
    "autocorrect": "",
    "deliverability": "safe",
    "quality_score": 92,
    "is_valid_format": true,
    "is_mx_found": true,
    "is_smtp_valid": true,
    "is_catch_all": false,
    "is_role_account": false,
    "is_disposable": false,
    "is_free": false,
    "result": "deliverable",
    "score": 92,
    "regexp": true,
    "gibberish": false,
    "mailbox_full": false,
    "disposable": false,
    "webmail": false,
    "suspect": false,
    "recent_domain": false,
    "valid_role": true,
    "sources": [
      {
        "domain": "example.com",
        "uri": "https://example.com/team",
        "extracted_on": "2023-06-01",
        "last_seen_on": "2023-12-01",
        "still_on_page": true
      }
    ]
  },
  "meta": {
    "params": {
      "email": "john.doe@example.com"
    }
  }
}
```

## Rate Limits
- Free tier: 100 verifications/day and 50 domain searches/month
- Paid plans:
  - Starter: 1,000 verifications/day
  - Growth: 10,000 verifications/day
  - Pro: 100,000 verifications/day
  - Enterprise: Custom

## Error Responses
- 400: Bad Request - Invalid parameters
- 401: Unauthorized - Invalid API key
- 403: Forbidden - Account inactive or suspended
- 429: Too Many Requests - Rate limit exceeded
- 500: Internal Server Error

## Implementation Notes
- Implement proper rate limiting handling
- Cache verification results to reduce API usage
- Handle different deliverability statuses appropriately
- Implement exponential backoff for rate limiting
- Validate email format before sending to API
- Consider bulk verification for efficiency
- Handle partial or incomplete data responses
- Respect privacy and only verify emails with permission