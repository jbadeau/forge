import React, { useState } from 'react';
import { Button, Input, Card } from '@my-org/ui-components';
import { StringUtils } from '@my-org/utils';

function App() {
  const [inputValue, setInputValue] = useState('');
  const [result, setResult] = useState('');

  const handleReverse = () => {
    setResult(StringUtils.reverse(inputValue));
  };

  const handleCapitalize = () => {
    setResult(StringUtils.capitalize(inputValue));
  };

  const handleClear = () => {
    setInputValue('');
    setResult('');
  };

  return (
    <div className="min-h-screen bg-gray-100 py-8">
      <div className="max-w-md mx-auto">
        <Card 
          title="String Utilities Demo"
          footer={
            <div className="text-sm text-gray-500">
              Powered by @my-org/utils and @my-org/ui-components
            </div>
          }
        >
          <div className="space-y-4">
            <Input
              label="Enter text"
              value={inputValue}
              onChange={setInputValue}
              placeholder="Type something..."
            />
            
            <div className="flex gap-2">
              <Button 
                onClick={handleReverse}
                disabled={StringUtils.isEmpty(inputValue)}
              >
                Reverse
              </Button>
              <Button 
                onClick={handleCapitalize}
                variant="secondary"
                disabled={StringUtils.isEmpty(inputValue)}
              >
                Capitalize
              </Button>
              <Button 
                onClick={handleClear}
                variant="danger"
                size="small"
              >
                Clear
              </Button>
            </div>
            
            {result && (
              <div className="p-3 bg-blue-50 border border-blue-200 rounded">
                <strong>Result:</strong> {result}
              </div>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}

export default App;