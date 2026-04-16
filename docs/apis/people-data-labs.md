# People Data Labs API Integration

## Overview
People Data Labs provides comprehensive professional profiles and contact information for individuals.

## API Endpoints

### Person Search
```
GET https://api.peopledatalabs.com/v5/person/search
```

Parameters:
- `sql`: SQL-like query to search for people
- `size`: Number of results to return (max 100)
- `from`: Offset for pagination

### Person Enrichment
```
GET https://api.peopledatalabs.com/v5/person/enrich
```

Parameters:
- `email`: Email address of the person
- `profile`: Social media profile URL
- `phone`: Phone number
- `first_name`: First name
- `last_name`: Last name
- `middle_name`: Middle name
- `location`: Location (city, state, country)
- `street_address`: Street address
- `postal_code`: Postal code
- `company`: Company name
- `school`: School name
- `job_title`: Job title
- `skills`: Skills separated by commas

### Person Identify
```
GET https://api.peopledatalabs.com/v5/person/identify
```

Parameters:
- Same as enrichment endpoint

## Authentication
Include your API key in the header:
```
Authorization: Bearer YOUR_API_KEY
```

## Response Format
Responses are in JSON format with the following structure:
```json
{
  "status": 200,
  "data": {
    "id": "person_id",
    "full_name": "John Doe",
    "first_name": "John",
    "last_name": "Doe",
    "job_history": [...],
    "education": [...],
    "profiles": [...],
    "location": "...",
    "emails": [...],
    "phones": [...],
    "summary": "..."
  },
  "size": 1,
  "total": 1
}
```

## Rate Limits
- Free tier: 1,000 requests per month
- Paid tiers: Higher rate limits available

## Error Responses
- 401: Invalid API key
- 402: Payment required
- 404: Person not found
- 429: Rate limit exceeded

## Implementation Notes
- Implement exponential backoff for rate limiting
- Cache responses when possible to reduce API usage
- Handle partial matches appropriately
- Sanitize user input before sending to API