import { StringUtils, ArrayUtils } from './index';

describe('StringUtils', () => {
  describe('isEmpty', () => {
    it('should return true for empty strings', () => {
      expect(StringUtils.isEmpty('')).toBe(true);
      expect(StringUtils.isEmpty('   ')).toBe(true);
      expect(StringUtils.isEmpty(null)).toBe(true);
      expect(StringUtils.isEmpty(undefined)).toBe(true);
    });

    it('should return false for non-empty strings', () => {
      expect(StringUtils.isEmpty('hello')).toBe(false);
      expect(StringUtils.isEmpty(' hello ')).toBe(false);
    });
  });

  describe('capitalize', () => {
    it('should capitalize first letter', () => {
      expect(StringUtils.capitalize('hello')).toBe('Hello');
      expect(StringUtils.capitalize('WORLD')).toBe('World');
    });
  });

  describe('reverse', () => {
    it('should reverse strings', () => {
      expect(StringUtils.reverse('hello')).toBe('olleh');
      expect(StringUtils.reverse('world')).toBe('dlrow');
    });
  });

  describe('truncate', () => {
    it('should truncate long strings', () => {
      expect(StringUtils.truncate('hello world', 5)).toBe('hello...');
      expect(StringUtils.truncate('short', 10)).toBe('short');
    });
  });
});

describe('ArrayUtils', () => {
  describe('unique', () => {
    it('should remove duplicates', () => {
      expect(ArrayUtils.unique([1, 2, 2, 3, 3, 4])).toEqual([1, 2, 3, 4]);
      expect(ArrayUtils.unique(['a', 'b', 'a', 'c'])).toEqual(['a', 'b', 'c']);
    });
  });

  describe('chunk', () => {
    it('should chunk arrays', () => {
      expect(ArrayUtils.chunk([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
      expect(ArrayUtils.chunk(['a', 'b', 'c', 'd'], 3)).toEqual([['a', 'b', 'c'], ['d']]);
    });
  });
});