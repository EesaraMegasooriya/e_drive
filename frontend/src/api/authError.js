const messages = {
  EMAIL_EXISTS: ["Email already registered", "An account already exists with this email address. Try signing in instead."],
  EMAIL_NOT_FOUND: ["Account not found", "No account uses this email address. Check the address or create an account."],
  INCORRECT_PASSWORD: ["Incorrect password", "The password you entered is incorrect. Try again or reset your password."],
  ACCOUNT_SUSPENDED: ["Account suspended", "This account has been suspended. Contact an administrator for help."],
  INVALID_RESET_LINK: ["Reset link unavailable", "This password-reset link is invalid or has expired. Request a new one."],
  VALIDATION_ERROR: ["Check your information", "Some submitted details are missing or invalid."],
  RATE_LIMITED: ["Too many attempts", "Please wait a few minutes before trying again."],
  SERVER_ERROR: ["Server error", "The server could not complete your request. Please try again shortly."],
};

export function authErrorDetails(error, fallbackAction = "complete this request") {
  const status = error.response?.status;
  const code = error.response?.data?.code;
  const serverMessage = error.response?.data?.message;

  if (messages[code]) {
    const [title, text] = messages[code];
    return { title, text: serverMessage || text };
  }
  if (error.code === "ECONNABORTED") {
    return { title: "Request timed out", text: "The server took too long to respond. Check your connection and try again." };
  }
  if (!error.response) {
    return { title: "Server unavailable", text: "EDrive may be under maintenance or offline. Please try again shortly." };
  }
  if ([502, 503, 504].includes(status)) {
    return { title: "Server under maintenance", text: "EDrive is temporarily unavailable. Please try again in a few minutes." };
  }
  if (status === 429) {
    return { title: "Too many attempts", text: "Please wait a few minutes before trying again." };
  }
  if (status === 400 && error.response?.data?.fieldErrors) {
    return { title: "Check your information", text: serverMessage || "Some submitted details are invalid." };
  }
  if (status === 401) {
    return { title: "Sign-in failed", text: serverMessage || "Your email or password is incorrect." };
  }
  return {
    title: "Something went wrong",
    text: serverMessage || `We couldn't ${fallbackAction}. Please try again.`,
  };
}
