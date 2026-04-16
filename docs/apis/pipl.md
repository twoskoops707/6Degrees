# Pipl API Integration

## Overview
Pipl is an identity resolution service that aggregates data from public sources to create comprehensive profiles.

## API Endpoints

### Find Person API
```
GET https://api.pipl.com/search/
```

Parameters:
- `first_name`: First name
- `last_name`: Last name
- `middle_name`: Middle name
- `raw_name`: Full name
- `email`: Email address
- `phone`: Phone number
- `username`: Username
- `user_id`: User ID
- `url`: Profile URL
- `country`: Country code
- `state`: State
- `city`: City
- `house`: House number
- `street`: Street name
- `zip_code`: ZIP code
- `raw_address`: Full address
- `from_age`: Minimum age
- `to_age`: Maximum age
- `person_id`: Pipl person ID
- `search_pointer`: Search pointer from a previous response
- `minimum_probability`: Minimum match probability (0.0-1.0)
- `minimum_match`: Minimum match criteria
- `show_sources`: Show sources ("matching", "all", "true")
- `live_feeds`: Include live feeds (true/false)
- `hide_sponsored`: Hide sponsored results (true/false)
- `infer_persons`: Infer related persons (true/false)
- `callback`: Callback URL for async requests

### Thumbnail API
```
GET https://api.pipl.com/thumbnail/
```

Parameters:
- `person_id`: Pipl person ID
- `thumbnail_token`: Thumbnail token from search response

## Authentication
Include your API key as a parameter:
```
?key=YOUR_API_KEY
```

## Response Format
Responses are in JSON format with the following structure:
```json
{
  "@http_status_code": 200,
  "warning": "Some warning message",
  "query": {
    "first_name": "John",
    "last_name": "Doe"
  },
  "match_requirements": "some requirement",
  "visible_sources": 10,
  "available_sources": 15,
  "estimated_results": 100,
  "person": {
    "id": "person_id",
    "url": "https://pipl.com/person/id/",
    "name": "John Doe",
    "names": [...],
    "addresses": [...],
    "phones": [...],
    "emails": [...],
    "jobs": [...],
    "educations": [...],
    "images": [...],
    "languages": [...],
    "ethnicities": [...],
    "origin_countries": [...],
    "urls": [...],
    "relationships": [...],
    "usernames": [...],
    "user_ids": [...],
    "dob": "1980-01-01",
    "gender": "male"
  },
  "sources": [...],
  "related_persons": [...],
  "@search_pointer": "search_pointer_value"
}
```

## Rate Limits
- Free tier: Limited number of requests per month
- Paid plans: Higher rate limits (typically 20-100 requests per second)

## Error Responses
- 400: Bad Request - Invalid parameters
- 401: Unauthorized - Invalid API key
- 403: Forbidden - Account inactive or suspended
- 404: Not Found - Person not found
- 406: Not Acceptable - Invalid format
- 429: Too Many Requests - Rate limit exceeded
- 500: Internal Server Error

## Implementation Notes
- Implement proper rate limiting handling
- Use search pointers for pagination when available
- Handle partial data responses appropriately
- Cache responses to reduce API usage
- Implement exponential backoff for rate limiting
- Validate user input before sending to API
- Handle different name formats and variations
- Consider privacy implications of data collection