import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { StringUtils } from '@my-org/utils';

const App = () => {
  const [inputValue, setInputValue] = useState('');
  const [result, setResult] = useState('');

  const handleReverse = () => {
    if (StringUtils.isEmpty(inputValue)) {
      Alert.alert('Error', 'Please enter some text first');
      return;
    }
    setResult(StringUtils.reverse(inputValue));
  };

  const handleCapitalize = () => {
    if (StringUtils.isEmpty(inputValue)) {
      Alert.alert('Error', 'Please enter some text first');
      return;
    }
    setResult(StringUtils.capitalize(inputValue));
  };

  const handleClear = () => {
    setInputValue('');
    setResult('');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>String Utilities Demo</Text>
      
      <View style={styles.card}>
        <TextInput
          style={styles.input}
          value={inputValue}
          onChangeText={setInputValue}
          placeholder="Type something..."
          placeholderTextColor="#666"
        />
        
        <View style={styles.buttonContainer}>
          <TouchableOpacity style={styles.button} onPress={handleReverse}>
            <Text style={styles.buttonText}>Reverse</Text>
          </TouchableOpacity>
          
          <TouchableOpacity style={[styles.button, styles.secondaryButton]} onPress={handleCapitalize}>
            <Text style={[styles.buttonText, styles.secondaryButtonText]}>Capitalize</Text>
          </TouchableOpacity>
          
          <TouchableOpacity style={[styles.button, styles.dangerButton]} onPress={handleClear}>
            <Text style={styles.buttonText}>Clear</Text>
          </TouchableOpacity>
        </View>
        
        {result ? (
          <View style={styles.result}>
            <Text style={styles.resultLabel}>Result:</Text>
            <Text style={styles.resultText}>{result}</Text>
          </View>
        ) : null}
      </View>
      
      <Text style={styles.footer}>
        Powered by @my-org/utils
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 20,
    paddingTop: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 20,
    color: '#333',
  },
  card: {
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    padding: 12,
    fontSize: 16,
    marginBottom: 16,
    backgroundColor: '#fff',
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  button: {
    backgroundColor: '#3b82f6',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 6,
    flex: 1,
    marginHorizontal: 4,
  },
  secondaryButton: {
    backgroundColor: '#e5e7eb',
  },
  dangerButton: {
    backgroundColor: '#dc2626',
  },
  buttonText: {
    color: 'white',
    textAlign: 'center',
    fontWeight: '600',
  },
  secondaryButtonText: {
    color: '#374151',
  },
  result: {
    backgroundColor: '#dbeafe',
    padding: 12,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#93c5fd',
  },
  resultLabel: {
    fontWeight: 'bold',
    marginBottom: 4,
  },
  resultText: {
    fontSize: 16,
  },
  footer: {
    textAlign: 'center',
    marginTop: 20,
    color: '#666',
    fontSize: 12,
  },
});

export default App;