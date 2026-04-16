# BuiltWith API Integration

## Overview
BuiltWith provides technology profiling for websites and companies, identifying the tools and services they use.

## API Endpoints

### Lookup API
```
GET https://api.builtwith.com/v20/api.json
```

Parameters:
- `KEY`: Your API key
- `LOOKUP`: Domain to lookup

### Detailed Lookup API
```
GET https://api.builtwith.com/v20/detailed/json
```

Parameters:
- `KEY`: Your API key
- `LOOKUP`: Domain to lookup
- `NOLIVE`: Skip live data (true/false)
- `NOHOST`: Exclude host information (true/false)
- `NOMETA`: Exclude meta information (true/false)

### Relationship API
```
GET https://api.builtwith.com/relations/v5/api.json
```

Parameters:
- `KEY`: Your API key
- `LOOKUP`: Domain to lookup
- `NOMETA`: Exclude meta information (true/false)

### Trends API
```
GET https://api.builtwith.com/trends/v8/api.json
```

Parameters:
- `KEY`: Your API key
- `TECH`: Technology to search for
- `COUNTRY`: Country code
- `DATE`: Date range (YYYY-MM-DD)

### Lists API
```
GET https://api.builtwith.com/lists/v3/list.json
```

Parameters:
- `KEY`: Your API key
- `LIST`: List ID

## Authentication
Include your API key as a parameter:
```
KEY=YOUR_API_KEY
```

## Response Format
Responses are in JSON format with the following structure:
```json
{
  "Errors": [],
  "Results": [
    {
      "Result": {
        "Domain": "example.com",
        "Url": "http://example.com",
        "SubDomain": "",
        "Favicon": "https://example.com/favicon.ico",
        "Categories": [
          {
            "Name": "Category Name",
            "Link": "https://builtwith.com/category/link"
          }
        ],
        "Technologies": [
          {
            "Name": "Technology Name",
            "Description": "Technology Description",
            "Link": "https://builtwith.com/technology/link",
            "Tag": "tag",
            "FirstDetected": 1234567890,
            "LastDetected": 1234567890,
            "Stats": {
              "AlexaRank": 1000,
              "SecurityScore": 85
            }
          }
        ],
        "Headers": {
          "Server": "nginx",
          "Content-Type": "text/html"
        },
        "Meta": {
          "Title": "Example Domain",
          "Description": "This domain is for use in illustrative examples...",
          "Keywords": "example,domain"
        },
        "DNS": {
          "A": ["93.184.216.34"],
          "MX": ["10 example.com"]
        },
        "Scripts": [
          "https://example.com/script.js"
        ],
        "Redirect": ""
      }
    }
  ]
}
```

## Rate Limits
- Various pricing tiers based on request volume:
  - Free tier: 100 requests/month
  - Starter: 500 requests/month
  - Professional: 5,000 requests/month
  - Business: 25,000 requests/month
  - Enterprise: Custom

## Error Responses
- 400: Bad Request - Invalid parameters
- 401: Unauthorized - Invalid API key
- 403: Forbidden - Account inactive or suspended
- 429: Too Many Requests - Rate limit exceeded
- 500: Internal Server Error

## Implementation Notes
- Implement proper rate limiting handling
- Cache responses to reduce API usage
- Handle multiple technology detections appropriately
- Parse and organize technology categories for user display
- Implement exponential backoff for rate limiting
- Validate domain input before sending to API
- Consider bulk lookups for efficiency when possible
- Handle expired or invalid domains gracefully