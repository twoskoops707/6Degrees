# Clearbit API Integration

## Overview
Clearbit provides business intelligence and company data including enrichment and discovery services.

## API Endpoints

### Person Enrichment
```
GET https://person.clearbit.com/v2/people/find
```

Parameters:
- `email`: Email address of the person
- `given_name`: First name
- `family_name`: Last name
- `company`: Company domain

### Company Enrichment
```
GET https://company.clearbit.com/v2/companies/find
```

Parameters:
- `domain`: Company domain
- `name`: Company name
- `linkedin`: LinkedIn URL

### Prospector API
```
POST https://prospector.clearbit.com/v1/people/search
```

Body Parameters:
- `query`: Search query
- `domain`: Company domain
- `role`: Job role
- `seniority`: Seniority level
- `titles`: Job titles
- `page`: Page number
- `page_size`: Results per page

### Discovery API
```
GET https://discovery.clearbit.com/v1/companies/search
```

Parameters:
- `query`: Search query
- `tags`: Company tags
- `tech`: Technologies used
- `employee_range`: Employee count range

## Authentication
Include your API key in the header:
```
Authorization: Bearer YOUR_API_KEY
```

## Response Format
Responses are in JSON format with the following structure:
```json
{
  "person": {
    "id": "person_id",
    "name": {
      "fullName": "John Doe",
      "givenName": "John",
      "familyName": "Doe"
    },
    "email": "john.doe@example.com",
    "location": "San Francisco, CA",
    "bio": "CEO at Example Inc.",
    "site": "https://johndoe.com",
    "avatar": "https://example.com/avatar.jpg",
    "employment": {
      "name": "Example Inc.",
      "title": "CEO",
      "domain": "example.com",
      "role": "management",
      "seniority": "executive"
    },
    "facebook": {
      "handle": "johndoe"
    },
    "twitter": {
      "handle": "johndoe",
      "id": "123456789",
      "bio": "CEO at Example Inc.",
      "followers": 5000,
      "following": 1000,
      "location": "San Francisco, CA",
      "site": "https://johndoe.com",
      "avatar": "https://example.com/avatar.jpg"
    },
    "linkedin": {
      "handle": "johndoe"
    },
    "github": {
      "handle": "johndoe",
      "id": "123456789",
      "avatar": "https://example.com/avatar.jpg",
      "company": "Example Inc.",
      "blog": "https://johndoe.com",
      "followers": 1000,
      "following": 500
    }
  }
}
```

## Rate Limits
- Free tier: Limited access with basic features
- Paid tiers: Higher rate limits (typically 600 requests per minute)

## Error Responses
- 401: Unauthorized
- 402: Payment Required
- 404: Person/Company Not Found
- 422: Unprocessable Entity
- 429: Too Many Requests

## Implementation Notes
- Implement proper error handling for different response codes
- Use caching to reduce API calls for frequently requested data
- Respect rate limits and implement backoff strategies
- Handle partial data responses gracefully
- Sanitize user input before making API requests