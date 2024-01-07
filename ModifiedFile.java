    // Check if the response has an error
    if (response.hasError()) {
        // Handle the error case
        ServiceError error = response.getError();
        System.err.println("Deploy failed: " + error.getMessage());
    } else if (response.hasResult()) {
        // Process the successful response
        String result = response.getResult();
        System.out.println("Deploy succeeded with result: " + result);
    } else {
        // Handle the case where neither error nor result is set
        System.err.println("Deploy response did not contain error or result.");
    }
