package utils

import "fmt"

// StringUtils provides common string utility functions
type StringUtils struct{}

// NewStringUtils creates a new StringUtils instance
func NewStringUtils() *StringUtils {
	return &StringUtils{}
}

// Reverse reverses a string
func (su *StringUtils) Reverse(s string) string {
	runes := []rune(s)
	for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
		runes[i], runes[j] = runes[j], runes[i]
	}
	return string(runes)
}

// IsEmpty checks if a string is empty
func (su *StringUtils) IsEmpty(s string) bool {
	return len(s) == 0
}

// Capitalize capitalizes the first letter of a string
func (su *StringUtils) Capitalize(s string) string {
	if len(s) == 0 {
		return s
	}
	return fmt.Sprintf("%c%s", s[0]-32, s[1:])
}