package sharedlib

import "strings"

// ToUpperCase converts a string to uppercase
func ToUpperCase(s string) string {
    return strings.ToUpper(s)
}

// IsEmpty checks if a string is empty or contains only whitespace
func IsEmpty(s string) bool {
    return strings.TrimSpace(s) == ""
}