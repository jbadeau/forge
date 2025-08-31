package utils

import "testing"

func TestStringUtils_Reverse(t *testing.T) {
	su := NewStringUtils()
	
	tests := []struct {
		input    string
		expected string
	}{
		{"hello", "olleh"},
		{"world", "dlrow"},
		{"", ""},
		{"a", "a"},
	}
	
	for _, test := range tests {
		result := su.Reverse(test.input)
		if result != test.expected {
			t.Errorf("Reverse(%s) = %s; want %s", test.input, result, test.expected)
		}
	}
}

func TestStringUtils_IsEmpty(t *testing.T) {
	su := NewStringUtils()
	
	if !su.IsEmpty("") {
		t.Error("IsEmpty(\"\") should return true")
	}
	
	if su.IsEmpty("hello") {
		t.Error("IsEmpty(\"hello\") should return false")
	}
}

func TestStringUtils_Capitalize(t *testing.T) {
	su := NewStringUtils()
	
	tests := []struct {
		input    string
		expected string
	}{
		{"hello", "Hello"},
		{"world", "World"},
		{"", ""},
		{"a", "A"},
	}
	
	for _, test := range tests {
		result := su.Capitalize(test.input)
		if result != test.expected {
			t.Errorf("Capitalize(%s) = %s; want %s", test.input, result, test.expected)
		}
	}
}