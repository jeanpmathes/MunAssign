# Excel Structure Analysis

**Source file:** `mymun-2026-04-25-1010-KAMUN  2025-Applications-Export.xlsx`  
**Generated on (UTC):** 2026-04-25T18:41:46Z

> Note: example values are intentionally redacted and replaced with placeholders to avoid exposing personally identifying information (PII).

## Workbook Overview

| Sheet | Data Rows | Columns |
|---|---:|---:|
| Chairs | 16 | 28 |
| Countries | 130 | 4 |
| Delegates | 157 | 31 |
| Delegations | 14 | 12 |
| Faculty Advisors | 11 | 19 |
| Observers | 2 | 19 |

## C# Modeling Notes

- Use one class per sheet for raw-import DTOs (e.g., `DelegateImportRow`, `ChairImportRow`).
- Keep **all `Question:` columns as `string?`** because they are free-text and sparse.
- Treat `Zip Code` as `string` even when numeric in some sheets, to preserve leading zeros/alphanumeric postcodes.
- Consider enums for low-cardinality text fields such as `Application Status`, `Diet`, and `Fee Paid`.

## Sheet: Chairs

- Header row: `1`
- Estimated data rows: `16`

| Excel Col | Header | Non-Null | Null | Unique | Estimated C# Type | Value Range / Length | Examples (Redacted) | Notes |
|---|---|---:|---:|---:|---|---|---|---|
| A | First Name | 16 | 0 | 16 | `string` | Length 3..9 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| B | Last Name | 16 | 0 | 16 | `string` | Length 4..10 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| C | Country | 16 | 0 | 4 | `string` | Length 7..11 | <country_1> \| <country_2> | Low cardinality (4 unique) => consider enum; examples redacted |
| D | City | 16 | 0 | 14 | `string` | Length 6..14 | <city_1> \| <city_2> | Examples redacted to avoid PII |
| E | address_street | 16 | 0 | 16 | `string` | Length 12..26 | <address_1> \| <address_2> | Examples redacted to avoid PII |
| F | Zip Code | 16 | 0 | 16 | `string` | Length 4..6 | <postal_code_1> \| <postal_code_2> | Examples redacted to avoid PII |
| G | Email | 16 | 0 | 16 | `string` | Length 18..40 | <email_1> \| <email_2> | Examples redacted to avoid PII |
| H | Application Status | 16 | 0 | 2 | `string` | Length 8..9 | <sample_1> \| <sample_2> | Low cardinality (2 unique) => consider enum; examples redacted |
| I | Submitted At | 16 | 0 | 7 | `DateOnly` | 2025-06-08 .. 2025-06-30 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| J | Created At | 16 | 0 | 11 | `DateOnly` | 2025-05-24 .. 2025-06-30 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| K | Diet | 16 | 0 | 3 | `string` | Length 5..10 | <sample_1> \| <sample_2> | Low cardinality (3 unique) => consider enum; examples redacted |
| L | Phone | 1 | 15 | 1 | `string` | Length 14..14 | <phone_1> | Examples redacted to avoid PII |
| M | Nationality | 16 | 0 | 6 | `string` | Length 5..24 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| N | Fee Paid | 16 | 0 | 1 | `bool` | Boolean-like values | <bool_true_or_false> | Examples redacted to avoid PII |
| O | Date of Birth | 16 | 0 | 16 | `DateOnly` | 1997-12-11 .. 2005-03-10 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| P | Delegation | 1 | 15 | 1 | `string` | Length 7..7 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| Q | Head Delegate/Faculty Advisor | 0 | 16 | 0 | `string?` | Always null in this export | <null> | - |
| R | Model Country assigned | 0 | 16 | 0 | `string?` | Always null in this export | <null> | - |
| S | Committee | 14 | 2 | 6 | `string` | Length 3..22 | <committee_1> \| <committee_2> | Examples redacted to avoid PII |
| T | Question: Please give Two topic suggestions for your first preference and atleast one for other preferences | 16 | 0 | 16 | `string` | Length 162..3619 | <free_text_redacted> | Examples redacted to avoid PII |
| U | Question: GIve us your spiciest political hot take ( we listen and we don't judge :P ) | 16 | 0 | 16 | `string` | Length 20..398 | <free_text_redacted> | Examples redacted to avoid PII |
| V | Question: What appeals to you about chairing at KAMUN 2025? | 16 | 0 | 16 | `string` | Length 61..2824 | <free_text_redacted> | Examples redacted to avoid PII |
| W | Question: Please briefly explain your past experiences in MUNs (Conferences you've been to, your roles, awards etc ) | 16 | 0 | 16 | `string` | Length 66..1983 | <free_text_redacted> | Examples redacted to avoid PII |
| X | Question: Explain your chairing style in three words | 16 | 0 | 16 | `string` | Length 15..1548 | <free_text_redacted> | Examples redacted to avoid PII |
| Y | Question: Explain your committee preferences | 16 | 0 | 16 | `string` | Length 291..2979 | <free_text_redacted> | Examples redacted to avoid PII |
| Z | Preference 1 | 16 | 0 | 3 | `string` | Length 3..16 | <sample_1> \| <sample_2> | Low cardinality (3 unique) => consider enum; examples redacted |
| AA | Preference 2 | 15 | 1 | 4 | `string` | Length 3..16 | <sample_1> \| <sample_2> | Low cardinality (4 unique) => consider enum; examples redacted |
| AB | Preference 3 | 10 | 6 | 5 | `string` | Length 3..16 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |

## Sheet: Countries

- Header row: `1`
- Estimated data rows: `130`

| Excel Col | Header | Non-Null | Null | Unique | Estimated C# Type | Value Range / Length | Examples (Redacted) | Notes |
|---|---|---:|---:|---:|---|---|---|---|
| A | Committee Name | 130 | 0 | 6 | `string` | Length 3..22 | <name_1> \| <name_2> | Low cardinality (6 unique) => consider enum; examples redacted |
| B | Member Name | 130 | 0 | 89 | `string` | Length 5..87 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| C | Delegation Name | 75 | 55 | 11 | `string` | Length 5..39 | <name_1> \| <name_2> | Low cardinality (11 unique) => consider enum; examples redacted |
| D | Delegates | 127 | 3 | 127 | `string` | Length 8..42 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |

## Sheet: Delegates

- Header row: `1`
- Estimated data rows: `157`

| Excel Col | Header | Non-Null | Null | Unique | Estimated C# Type | Value Range / Length | Examples (Redacted) | Notes |
|---|---|---:|---:|---:|---|---|---|---|
| A | First Name | 157 | 0 | 151 | `string` | Length 3..17 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| B | Last Name | 157 | 0 | 152 | `string` | Length 3..19 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| C | Country | 154 | 3 | 14 | `string` | Length 5..11 | <country_1> \| <country_2> | Examples redacted to avoid PII |
| D | City | 157 | 0 | 97 | `string` | Length 4..26 | <city_1> \| <city_2> | Examples redacted to avoid PII |
| E | address_street | 157 | 0 | 152 | `string` | Length 4..79 | <address_1> \| <address_2> | Examples redacted to avoid PII |
| F | Zip Code | 157 | 0 | 107 | `string` | Length 4..8 | <postal_code_1> \| <postal_code_2> | Examples redacted to avoid PII |
| G | Email | 157 | 0 | 157 | `string` | Length 13..46 | <email_1> \| <email_2> | Examples redacted to avoid PII |
| H | Application Status | 157 | 0 | 4 | `string` | Length 8..10 | <sample_1> \| <sample_2> | Low cardinality (4 unique) => consider enum; examples redacted |
| I | Submitted At | 157 | 0 | 58 | `DateOnly` | 2025-08-04 .. 2025-12-05 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| J | Created At | 157 | 0 | 59 | `DateOnly` | 2025-08-04 .. 2025-12-05 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| K | Diet | 154 | 3 | 4 | `string` | Length 5..10 | <sample_1> \| <sample_2> | Low cardinality (4 unique) => consider enum; examples redacted |
| L | Phone | 1 | 156 | 1 | `string` | Length 14..14 | <phone_1> | Examples redacted to avoid PII |
| M | Nationality | 154 | 3 | 28 | `string` | Length 5..20 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| N | Fee Paid | 157 | 0 | 2 | `bool` | Boolean-like values | <bool_true_or_false> | Examples redacted to avoid PII |
| O | Date of Birth | 157 | 0 | 148 | `DateOnly` | 1995-08-11 .. 2010-10-16 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| P | Delegation | 87 | 70 | 11 | `string` | Length 5..39 | <sample_1> \| <sample_2> | Low cardinality (11 unique) => consider enum; examples redacted |
| Q | Head Delegate/Faculty Advisor | 5 | 152 | 1 | `string` | Length 13..13 | <sample_1> \| <sample_2> | Low cardinality (1 unique) => consider enum; examples redacted |
| R | Model Country assigned | 137 | 20 | 86 | `string` | Length 5..87 | <country_1> \| <country_2> | Examples redacted to avoid PII |
| S | Committee | 137 | 20 | 6 | `string` | Length 3..22 | <committee_1> \| <committee_2> | Low cardinality (6 unique) => consider enum; examples redacted |
| T | Question: Are you part of any delegation? | 111 | 46 | 84 | `string` | Length 1..322 | <free_text_redacted> | Examples redacted to avoid PII |
| U | Question: What is your level of English? (Please note that the conference will take place in English) | 146 | 11 | 2 | `string` | Length 23..24 | <free_text_redacted> | Low cardinality (2 unique) => consider enum; examples redacted |
| V | Question: Please describe any of your previous Model United Nations or similar experiences. (such as school debates, mock debates, diplomacy workshops or other student-led debates) | 146 | 11 | 142 | `string` | Length 1..1183 | <free_text_redacted> | Examples redacted to avoid PII |
| W | Question: Do you need Visa Support? If yes, provide your passport number and date of birth. | 59 | 98 | 15 | `string` | Length 1..111 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| X | Question: Please explain your choice of comittees and countries. (write at least three sentences, also refer to the topic you have chosen) | 146 | 11 | 146 | `string` | Length 1..1729 | <free_text_redacted> | Examples redacted to avoid PII |
| Y | Question: Any song you would like to hear at KAMUN 2025? | 76 | 81 | 74 | `string` | Length 1..142 | <free_text_redacted> | Examples redacted to avoid PII |
| Z | Question: Are you a high-schooler? | 146 | 11 | 2 | `bool` | Boolean-like values | <bool_true_or_false> | Examples redacted to avoid PII |
| AA | Question: What category would best describe your MUN experience level? | 146 | 11 | 4 | `string` | Length 8..12 | <free_text_redacted> | Low cardinality (4 unique) => consider enum; examples redacted |
| AB | Question: If you could represent any fictional delegation at a United Nations conference, what would you pick? | 59 | 98 | 55 | `string` | Length 1..975 | <free_text_redacted> | Examples redacted to avoid PII |
| AC | Preference 1 | 144 | 13 | 78 | `string` | Length 14..93 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| AD | Preference 2 | 144 | 13 | 60 | `string` | Length 14..81 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| AE | Preference 3 | 143 | 14 | 71 | `string` | Length 14..93 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |

## Sheet: Delegations

- Header row: `1`
- Estimated data rows: `14`

| Excel Col | Header | Non-Null | Null | Unique | Estimated C# Type | Value Range / Length | Examples (Redacted) | Notes |
|---|---|---:|---:|---:|---|---|---|---|
| A | Delegation Name | 14 | 0 | 14 | `string` | Length 5..39 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| B | Head Delegate/Faculty Advisor first name | 12 | 2 | 12 | `string` | Length 3..11 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| C | Head Delegate/Faculty Advisor last name | 12 | 2 | 12 | `string` | Length 4..11 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| D | Head Delegate/Faculty Advisor email | 12 | 2 | 12 | `string` | Length 13..36 | <email_1> \| <email_2> | Examples redacted to avoid PII |
| E | Current Delegates | 14 | 0 | 9 | `int` | 0 .. 17 | <int_value> | Examples redacted to avoid PII |
| F | Country | 14 | 0 | 4 | `string` | Length 6..11 | <country_1> \| <country_2> | Low cardinality (4 unique) => consider enum; examples redacted |
| G | Expected Delegates | 14 | 0 | 9 | `int` | 4 .. 20 | <int_value> | Examples redacted to avoid PII |
| H | Application Status | 14 | 0 | 3 | `string` | Length 8..9 | <sample_1> \| <sample_2> | Low cardinality (3 unique) => consider enum; examples redacted |
| I | Submitted At | 14 | 0 | 14 | `DateOnly` | 2025-08-08 .. 2025-11-04 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| J | Created At | 14 | 0 | 14 | `DateOnly` | 2025-08-08 .. 2025-11-02 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| K | Head Delegate Fees Paid | 14 | 0 | 2 | `bool` | Boolean-like values | <bool_true_or_false> | Examples redacted to avoid PII |
| L | Delegate Fees Paid | 14 | 0 | 11 | `int` | 0 .. 26 | <int_value> | Examples redacted to avoid PII |

## Sheet: Faculty Advisors

- Header row: `1`
- Estimated data rows: `11`

| Excel Col | Header | Non-Null | Null | Unique | Estimated C# Type | Value Range / Length | Examples (Redacted) | Notes |
|---|---|---:|---:|---:|---|---|---|---|
| A | First Name | 11 | 0 | 11 | `string` | Length 3..11 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| B | Last Name | 11 | 0 | 11 | `string` | Length 4..11 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| C | Country | 10 | 1 | 4 | `string` | Length 6..11 | <country_1> \| <country_2> | Examples redacted to avoid PII |
| D | City | 11 | 0 | 8 | `string` | Length 4..15 | <city_1> \| <city_2> | Examples redacted to avoid PII |
| E | address_street | 11 | 0 | 10 | `string` | Length 4..76 | <address_1> \| <address_2> | Examples redacted to avoid PII |
| F | Zip Code | 11 | 0 | 8 | `string` | Length 4..5 | <postal_code_1> \| <postal_code_2> | Examples redacted to avoid PII |
| G | Email | 11 | 0 | 11 | `string` | Length 13..36 | <email_1> \| <email_2> | Examples redacted to avoid PII |
| H | Application Status | 11 | 0 | 1 | `string` | Length 8..8 | <sample_1> \| <sample_2> | Low cardinality (1 unique) => consider enum; examples redacted |
| I | Submitted At | 11 | 0 | 10 | `DateOnly` | 2025-08-08 .. 2025-11-03 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| J | Created At | 11 | 0 | 11 | `DateOnly` | 2025-08-06 .. 2025-11-01 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| K | Diet | 10 | 1 | 3 | `string` | Length 5..10 | <sample_1> \| <sample_2> | Low cardinality (3 unique) => consider enum; examples redacted |
| L | Phone | 0 | 11 | 0 | `string?` | Always null in this export | <null> | - |
| M | Nationality | 8 | 3 | 5 | `string` | Length 6..24 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| N | Fee Paid | 11 | 0 | 2 | `bool` | Boolean-like values | <bool_true_or_false> | Examples redacted to avoid PII |
| O | Date of Birth | 11 | 0 | 11 | `DateOnly` | 1969-03-21 .. 2004-08-24 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| P | Delegation | 11 | 0 | 7 | `string` | Length 18..39 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| Q | Head Delegate/Faculty Advisor | 11 | 0 | 1 | `string` | Length 15..15 | <sample_1> \| <sample_2> | Low cardinality (1 unique) => consider enum; examples redacted |
| R | Model Country assigned | 0 | 11 | 0 | `string?` | Always null in this export | <null> | - |
| S | Committee | 0 | 11 | 0 | `string?` | Always null in this export | <null> | - |

## Sheet: Observers

- Header row: `1`
- Estimated data rows: `2`

| Excel Col | Header | Non-Null | Null | Unique | Estimated C# Type | Value Range / Length | Examples (Redacted) | Notes |
|---|---|---:|---:|---:|---|---|---|---|
| A | First Name | 2 | 0 | 2 | `string` | Length 6..7 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| B | Last Name | 2 | 0 | 2 | `string` | Length 6..7 | <name_1> \| <name_2> | Examples redacted to avoid PII |
| C | Country | 2 | 0 | 2 | `string` | Length 6..7 | <country_1> \| <country_2> | Examples redacted to avoid PII |
| D | City | 2 | 0 | 2 | `string` | Length 9..21 | <city_1> \| <city_2> | Examples redacted to avoid PII |
| E | address_street | 2 | 0 | 2 | `string` | Length 10..22 | <address_1> \| <address_2> | Examples redacted to avoid PII |
| F | Zip Code | 2 | 0 | 2 | `string` | Length 5..5 | <postal_code_1> \| <postal_code_2> | Zip-like field (preserve formatting); examples redacted |
| G | Email | 2 | 0 | 2 | `string` | Length 21..26 | <email_1> \| <email_2> | Examples redacted to avoid PII |
| H | Application Status | 2 | 0 | 2 | `string` | Length 8..9 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| I | Submitted At | 2 | 0 | 2 | `DateOnly` | 2025-06-14 .. 2025-11-15 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| J | Created At | 2 | 0 | 2 | `DateOnly` | 2025-05-02 .. 2025-11-15 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| K | Diet | 2 | 0 | 1 | `string` | Length 8..8 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| L | Phone | 0 | 2 | 0 | `string?` | Always null in this export | <null> | - |
| M | Nationality | 2 | 0 | 2 | `string` | Length 6..7 | <sample_1> \| <sample_2> | Examples redacted to avoid PII |
| N | Fee Paid | 2 | 0 | 1 | `bool` | Boolean-like values | <bool_true_or_false> | Examples redacted to avoid PII |
| O | Date of Birth | 2 | 0 | 2 | `DateOnly` | 2005-05-24 .. 2009-06-06 | <yyyy-mm-dd> | Examples redacted to avoid PII |
| P | Delegation | 0 | 2 | 0 | `string?` | Always null in this export | <null> | - |
| Q | Head Delegate/Faculty Advisor | 0 | 2 | 0 | `string?` | Always null in this export | <null> | - |
| R | Model Country assigned | 0 | 2 | 0 | `string?` | Always null in this export | <null> | - |
| S | Committee | 0 | 2 | 0 | `string?` | Always null in this export | <null> | - |

## Additional Observations

- The workbook appears to contain role-separated application exports (`Chairs`, `Delegates`, `Faculty Advisors`, `Observers`) plus assignment helper sheets (`Countries`, `Delegations`).
- Date fields (`Submitted At`, `Created At`, `Date of Birth`) are consistently ISO-style text (`yyyy-MM-dd`) in this export.
- `Phone` is mostly empty across sheets; model it as nullable.
- `Delegation`, `Committee`, and `Model Country assigned` are sparsely populated in some role sheets and should be nullable.
