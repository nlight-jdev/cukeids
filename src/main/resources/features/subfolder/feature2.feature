Feature: Examples used for scenario tagging using CukeIds

Scenario: Complete all incomplete todos
  Given the following todos exist:
    | title        | author                    | complete |
    | Pick up milk | email: person@example.com | false    |
    | Pick up eggs | email: person@example.com | false    |
  And I have signed in as "person@example.com"
  When I complete the todo "Pick up milk"
  And I complete the todo "Pick up eggs"
  Then I should have no incomplete todos
  
Scenario: Successful login
  Given a user "Aslak" with password "xyz"
  And I am on the login page
  And I fill in "User name" with "Aslak"
  And I fill in "Password" with "xyz"
  When I press "Log in"
  Then I should see "Welcome, Aslak"
  
Scenario: User is greeted upon login
  Given the user "Aslak" has an account
  When he logs in
  Then he should see "Welcome, Aslak"