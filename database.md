# Database Structure (Room Database)

## Database Details
- **Database Class:** `AppDatabase`
- **Version:** 1
- **Export Schema:** False

---

## Tables

### 1. `users`
Stores information about registered users in the application.

| Column Name | Data Type | Constraints / Default Value | Description |
| :--- | :--- | :--- | :--- |
| `id` | Integer | **Primary Key**, Auto-generated (0) | Unique identifier for the user. |
| `name` | String | Not Null | The full name of the user. |
| `email` | String | Not Null | The email address of the user. |
| `timestamp` | Long | `System.currentTimeMillis()` | The time the user was created. |

---

## Data Access Object (DAO) Operations

### `UserDao`
Provides the following methods to interact with the `users` table:

*   **`getAllUsers()`**: Retrieves a stream (`Flow<List<User>>`) containing all users, ordered by `timestamp` descending (newest first).
*   **`insertUser(user: User)`**: Inserts a new user into the database. Replaces the existing record if there is a conflict.
*   **`deleteUserById(id: Int)`**: Deletes a specific user based on their unique `id`.
